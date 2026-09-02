package tallyvane.identity.application.port

import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.user.UserId

internal class CredentialRepositoryFake : CredentialRepository {
    private val byUser = mutableMapOf<UserId, MutableList<Credential>>()

    override suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord? =
        byUser[userId].orEmpty().filterIsInstance<Credential.PasswordRecord>().firstOrNull()

    override suspend fun save(userId: UserId, credential: Credential) {
        byUser.getOrPut(userId) { mutableListOf() }.add(credential)
    }
}
