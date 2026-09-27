package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret

/**
 * [CredentialRepository] over [PasswordCredentialsTable] and [GoogleCredentialsTable], for a real
 * Postgres. Opens no transaction of its own — see that port's own KDoc for why.
 *
 * [save] does not report a unique-violation outcome the way [UserRepository.insert] does — a
 * second account presenting the same [GoogleSubject] this repository has never had a caller for
 * yet, since every existing call site checks [findUserIdByGoogleSubject] first inside the same
 * transaction and only calls [save] for a subject it just confirmed is unclaimed. A real
 * concurrent race between two such calls would surface as an uncaught constraint violation rather
 * than a graceful outcome — a known, open gap, not a silent one: `application/README.md`.
 */
internal class CredentialRepositoryOverExposed(
    private val realm: IdentityRealm = IdentityRealm.USER,
    adminEmails: Set<String> = emptySet(),
) : CredentialRepository {
    private val admins = adminEmails.mapTo(mutableSetOf()) { it.trim().lowercase() }

    override suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord? = when (realm) {
        IdentityRealm.USER -> PasswordCredentialsTable.selectAll()
            .where { PasswordCredentialsTable.userId eq userId.value }
            .singleOrNull()
            ?.let { row -> Credential.PasswordRecord(PasswordHash(Secret(row[PasswordCredentialsTable.passwordHash]))) }
        IdentityRealm.ADMIN -> AdminPasswordCredentialsTable.selectAll()
            .where { AdminPasswordCredentialsTable.adminId eq userId.value }
            .singleOrNull()
            ?.let { row ->
                Credential.PasswordRecord(PasswordHash(Secret(row[AdminPasswordCredentialsTable.passwordHash])))
            }
    }

    override suspend fun findGoogleFor(userId: UserId): Credential.GoogleRecord? = when (realm) {
        IdentityRealm.USER -> GoogleCredentialsTable.selectAll()
            .where { GoogleCredentialsTable.userId eq userId.value }
            .singleOrNull()
            ?.let { row -> Credential.GoogleRecord(GoogleSubject(row[GoogleCredentialsTable.googleSubject])) }
        IdentityRealm.ADMIN -> AdminGoogleCredentialsTable.selectAll()
            .where { AdminGoogleCredentialsTable.adminId eq userId.value }
            .singleOrNull()
            ?.let { row -> Credential.GoogleRecord(GoogleSubject(row[AdminGoogleCredentialsTable.googleSubject])) }
    }

    override suspend fun findUserIdByGoogleSubject(subject: GoogleSubject): UserId? = when (realm) {
        IdentityRealm.USER -> GoogleCredentialsTable.selectAll()
            .where { GoogleCredentialsTable.googleSubject eq subject.value }
            .singleOrNull()
            ?.let { row -> UserId(row[GoogleCredentialsTable.userId]) }
        IdentityRealm.ADMIN -> {
            val adminId = AdminGoogleCredentialsTable.selectAll()
                .where { AdminGoogleCredentialsTable.googleSubject eq subject.value }
                .singleOrNull()
                ?.let { row -> UserId(row[AdminGoogleCredentialsTable.adminId]) }
                ?: return null
            val email = AdminsTable.selectAll()
                .where { AdminsTable.id eq adminId.value }
                .singleOrNull()
                ?.get(AdminsTable.email)
            adminId.takeIf { email?.lowercase()?.let(admins::contains) == true }
        }
    }

    override suspend fun deleteGoogleFor(userId: UserId): Boolean = when (realm) {
        IdentityRealm.USER -> GoogleCredentialsTable.deleteWhere { GoogleCredentialsTable.userId eq userId.value } > 0
        IdentityRealm.ADMIN -> AdminGoogleCredentialsTable.deleteWhere {
            AdminGoogleCredentialsTable.adminId eq
                userId.value
        } >
            0
    }

    override suspend fun saveGoogleIfUnclaimed(userId: UserId, subject: GoogleSubject): Boolean = when (realm) {
        IdentityRealm.USER -> GoogleCredentialsTable.insertIgnore {
            it[GoogleCredentialsTable.userId] = userId.value
            it[googleSubject] = subject.value
        }.insertedCount == 1
        IdentityRealm.ADMIN -> AdminGoogleCredentialsTable.insertIgnore {
            it[AdminGoogleCredentialsTable.adminId] = userId.value
            it[AdminGoogleCredentialsTable.googleSubject] = subject.value
        }.insertedCount == 1
    }

    override suspend fun save(userId: UserId, credential: Credential) {
        when (credential) {
            is Credential.PasswordRecord -> when (realm) {
                IdentityRealm.USER -> PasswordCredentialsTable.insert {
                    it[PasswordCredentialsTable.userId] = userId.value
                    it[passwordHash] = credential.hash.encoded.revealed()
                }
                IdentityRealm.ADMIN -> AdminPasswordCredentialsTable.insert {
                    it[AdminPasswordCredentialsTable.adminId] = userId.value
                    it[AdminPasswordCredentialsTable.passwordHash] = credential.hash.encoded.revealed()
                }
            }
            is Credential.GoogleRecord -> when (realm) {
                IdentityRealm.USER -> GoogleCredentialsTable.insert {
                    it[GoogleCredentialsTable.userId] = userId.value
                    it[googleSubject] = credential.subject.value
                }
                IdentityRealm.ADMIN -> AdminGoogleCredentialsTable.insert {
                    it[AdminGoogleCredentialsTable.adminId] = userId.value
                    it[AdminGoogleCredentialsTable.googleSubject] = credential.subject.value
                }
            }
        }
    }

    override suspend fun saveOrReplacePasswordFor(userId: UserId, credential: Credential.PasswordRecord) {
        when (realm) {
            IdentityRealm.USER -> PasswordCredentialsTable.upsert(PasswordCredentialsTable.userId) {
                it[PasswordCredentialsTable.userId] = userId.value
                it[PasswordCredentialsTable.passwordHash] = credential.hash.encoded.revealed()
            }
            IdentityRealm.ADMIN -> AdminPasswordCredentialsTable.upsert(AdminPasswordCredentialsTable.adminId) {
                it[AdminPasswordCredentialsTable.adminId] = userId.value
                it[AdminPasswordCredentialsTable.passwordHash] = credential.hash.encoded.revealed()
            }
        }
    }
}
