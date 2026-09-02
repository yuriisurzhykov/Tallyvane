package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId

/**
 * A [PendingAuthenticationStore] backed by an in-memory map, for a use case's test to inspect what
 * was saved without a real database.
 */
internal class PendingAuthenticationStoreFake : PendingAuthenticationStore {
    val saved: MutableMap<PendingAuthenticationId, PendingAuthentication> = mutableMapOf()

    override suspend fun save(pending: PendingAuthentication) {
        saved[pending.id] = pending
    }

    override suspend fun find(id: PendingAuthenticationId): PendingAuthentication? = saved[id]

    override suspend fun delete(id: PendingAuthenticationId) {
        saved.remove(id)
    }
}
