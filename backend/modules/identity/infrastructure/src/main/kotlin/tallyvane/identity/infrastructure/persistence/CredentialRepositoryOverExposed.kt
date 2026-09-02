package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
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
internal class CredentialRepositoryOverExposed : CredentialRepository {
    override suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord? = PasswordCredentialsTable
        .selectAll()
        .where { PasswordCredentialsTable.userId eq userId.value }
        .singleOrNull()
        ?.let { row -> Credential.PasswordRecord(PasswordHash(Secret(row[PasswordCredentialsTable.passwordHash]))) }

    override suspend fun findUserIdByGoogleSubject(subject: GoogleSubject): UserId? = GoogleCredentialsTable
        .selectAll()
        .where { GoogleCredentialsTable.googleSubject eq subject.value }
        .singleOrNull()
        ?.let { row -> UserId(row[GoogleCredentialsTable.userId]) }

    override suspend fun save(userId: UserId, credential: Credential) {
        when (credential) {
            is Credential.PasswordRecord -> PasswordCredentialsTable.insert {
                it[PasswordCredentialsTable.userId] = userId.value
                it[passwordHash] = credential.hash.encoded.revealed()
            }

            is Credential.GoogleRecord -> GoogleCredentialsTable.insert {
                it[GoogleCredentialsTable.userId] = userId.value
                it[googleSubject] = credential.subject.value
            }
        }
    }
}
