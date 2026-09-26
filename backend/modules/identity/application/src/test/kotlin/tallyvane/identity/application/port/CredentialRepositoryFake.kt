package tallyvane.identity.application.port

import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.UserId

internal class CredentialRepositoryFake : CredentialRepository {
    private val byUser = mutableMapOf<UserId, MutableList<Credential>>()

    override suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord? =
        byUser[userId].orEmpty().filterIsInstance<Credential.PasswordRecord>().firstOrNull()

    override suspend fun findGoogleFor(userId: UserId): Credential.GoogleRecord? =
        byUser[userId].orEmpty().filterIsInstance<Credential.GoogleRecord>().firstOrNull()

    override suspend fun findUserIdByGoogleSubject(subject: GoogleSubject): UserId? = byUser.entries
        .firstOrNull { (_, credentials) -> credentials.any { it == Credential.GoogleRecord(subject) } }
        ?.key

    override suspend fun deleteGoogleFor(userId: UserId): Boolean =
        byUser[userId]?.removeIf { it is Credential.GoogleRecord } == true

    override suspend fun saveGoogleIfUnclaimed(userId: UserId, subject: GoogleSubject): Boolean {
        if (findGoogleFor(userId) != null || findUserIdByGoogleSubject(subject) != null) return false
        save(userId, Credential.GoogleRecord(subject))
        return true
    }

    override suspend fun save(userId: UserId, credential: Credential) {
        byUser.getOrPut(userId) { mutableListOf() }.add(credential)
    }

    override suspend fun saveOrReplacePasswordFor(userId: UserId, credential: Credential.PasswordRecord) {
        val credentials = byUser.getOrPut(userId) { mutableListOf() }
        credentials.removeAll { it is Credential.PasswordRecord }
        credentials.add(credential)
    }
}
