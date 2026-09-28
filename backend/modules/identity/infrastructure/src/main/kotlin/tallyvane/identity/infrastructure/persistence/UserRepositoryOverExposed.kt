package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId

/**
 * [UserRepository] over [UsersTable], for a real Postgres. Opens no transaction of its own — see
 * that port's own KDoc for why.
 */
internal class UserRepositoryOverExposed(
    private val realm: IdentityRealm = IdentityRealm.USER,
    adminEmails: Set<String> = emptySet(),
) : UserRepository {
    private val instant = InstantColumn()
    private val admins = adminEmails.mapTo(mutableSetOf()) { it.trim().lowercase() }

    override suspend fun findByEmail(email: Email): User? = when (realm) {
        IdentityRealm.USER -> UsersTable.selectAll().where { UsersTable.email eq email.value }.singleOrNull()?.toUser()
        IdentityRealm.ADMIN -> if (email.value.lowercase() in admins) {
            AdminsTable.selectAll().where { AdminsTable.email eq email.value }.singleOrNull()?.toAdmin()
        } else {
            null
        }
    }

    override suspend fun findById(id: UserId): User? = when (realm) {
        IdentityRealm.USER -> UsersTable.selectAll().where { UsersTable.id eq id.value }.singleOrNull()?.toUser()
        IdentityRealm.ADMIN -> AdminsTable.selectAll().where { AdminsTable.id eq id.value }.singleOrNull()?.toAdmin()
    }

    override suspend fun markEmailVerified(id: UserId): Boolean = when (realm) {
        IdentityRealm.USER -> UsersTable.update({ UsersTable.id eq id.value }) { it[emailVerified] = true } == 1
        IdentityRealm.ADMIN -> AdminsTable.update({
            AdminsTable.id eq id.value
        }) { it[AdminsTable.emailVerified] = true } ==
            1
    }

    override suspend fun updateDisplayName(id: UserId, displayName: String?): Boolean = when (realm) {
        IdentityRealm.USER -> UsersTable.update({ UsersTable.id eq id.value }) {
            it[UsersTable.displayName] = displayName
        } == 1
        IdentityRealm.ADMIN -> AdminsTable.update({ AdminsTable.id eq id.value }) {
            it[AdminsTable.displayName] = displayName
        } == 1
    }

    override suspend fun updateSecurityEmails(id: UserId, enabled: Boolean): Boolean = when (realm) {
        IdentityRealm.USER -> UsersTable.update({ UsersTable.id eq id.value }) {
            it[securityEmailsEnabled] = enabled
        } == 1
        IdentityRealm.ADMIN -> false
    }

    /**
     * Guarded by a savepoint, not a preceding [findByEmail] — that check-then-act would race
     * under `READ COMMITTED`, which is exactly [UserRepository.insert]'s own KDoc. Without the
     * savepoint, PostgreSQL would abort the whole surrounding transaction on the very unique
     * violation this method exists to turn into an ordinary outcome — verified against a real
     * database, not assumed: `backend/playground/savepoints/README.md`'s 2026-09-02 entry.
     *
     * Reports every unique violation as [UserRepository.InsertOutcome.EMAIL_TAKEN], not only one
     * confirmed against the `email` constraint by name — the only other unique constraint on this
     * table is the primary key, and [id] colliding is a version-7 UUID producing the same value
     * twice, which is not a real operational risk here.
     */
    override suspend fun insert(user: User): UserRepository.InsertOutcome {
        val connection = TransactionManager.current().connection
        if (realm == IdentityRealm.ADMIN && user.email.value.lowercase() !in admins) {
            return UserRepository.InsertOutcome.EMAIL_TAKEN
        }
        val savepoint = connection.setSavepoint("${realm.name.lowercase()}_insert")
        return try {
            when (realm) {
                IdentityRealm.USER -> UsersTable.insert {
                    it[id] = user.id.value
                    it[email] = user.email.value
                    it[displayName] = user.displayName
                    it[securityEmailsEnabled] = user.securityEmailsEnabled
                    it[createdAt] = instant.toColumn(user.createdAt)
                    it[disabledAt] = user.disabledAt?.let(instant::toColumn)
                    it[UsersTable.emailVerified] = user.emailVerified
                }
                IdentityRealm.ADMIN -> AdminsTable.insert {
                    it[AdminsTable.id] = user.id.value
                    it[AdminsTable.email] = user.email.value
                    it[AdminsTable.displayName] = user.displayName
                    it[AdminsTable.createdAt] = instant.toColumn(user.createdAt)
                    it[AdminsTable.disabledAt] = user.disabledAt?.let(instant::toColumn)
                    it[AdminsTable.emailVerified] = user.emailVerified
                }
            }
            connection.releaseSavepoint(savepoint)
            UserRepository.InsertOutcome.INSERTED
        } catch (cause: ExposedSQLException) {
            connection.rollback(savepoint)
            if (cause.sqlState == UNIQUE_VIOLATION) {
                UserRepository.InsertOutcome.EMAIL_TAKEN
            } else {
                throw cause
            }
        }
    }

    private fun ResultRow.toUser(): User = User(
        id = UserId(this[UsersTable.id]),
        email = Email(this[UsersTable.email]),
        displayName = this[UsersTable.displayName],
        securityEmailsEnabled = this[UsersTable.securityEmailsEnabled],
        createdAt = instant.toDomain(this[UsersTable.createdAt]),
        disabledAt = this[UsersTable.disabledAt]?.let(instant::toDomain),
        emailVerified = this[UsersTable.emailVerified],
    )

    private fun ResultRow.toAdmin(): User = User(
        id = UserId(this[AdminsTable.id]),
        email = Email(this[AdminsTable.email]),
        displayName = this[AdminsTable.displayName],
        createdAt = instant.toDomain(this[AdminsTable.createdAt]),
        disabledAt = this[AdminsTable.disabledAt]?.let(instant::toDomain),
        emailVerified = this[AdminsTable.emailVerified],
    )

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
