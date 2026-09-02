package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId

/**
 * [UserRepository] over [UsersTable], for a real Postgres. Opens no transaction of its own — see
 * that port's own KDoc for why.
 */
internal class UserRepositoryOverExposed : UserRepository {
    private val instant = InstantColumn()

    override suspend fun findByEmail(email: Email): User? =
        UsersTable.selectAll().where { UsersTable.email eq email.value }.singleOrNull()?.toUser()

    override suspend fun findById(id: UserId): User? =
        UsersTable.selectAll().where { UsersTable.id eq id.value }.singleOrNull()?.toUser()

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
        val savepoint = connection.setSavepoint("user_insert")
        return try {
            UsersTable.insert {
                it[id] = user.id.value
                it[email] = user.email.value
                it[displayName] = user.displayName
                it[createdAt] = instant.toColumn(user.createdAt)
                it[disabledAt] = user.disabledAt?.let(instant::toColumn)
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
        createdAt = instant.toDomain(this[UsersTable.createdAt]),
        disabledAt = this[UsersTable.disabledAt]?.let(instant::toDomain),
    )

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
