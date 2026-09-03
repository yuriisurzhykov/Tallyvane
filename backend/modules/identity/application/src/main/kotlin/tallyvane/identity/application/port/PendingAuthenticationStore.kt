package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId

/**
 * Where a [PendingAuthentication] lives between a primary credential checking out and a second
 * factor completing it.
 *
 * No real implementation exists yet; only a handwritten fake in this module's own tests satisfies
 * this interface until the persistence slice designs the storage shape, the same open state
 * [SessionStore] is already in.
 */
public interface PendingAuthenticationStore {
    public suspend fun save(pending: PendingAuthentication)

    public suspend fun find(id: PendingAuthenticationId): PendingAuthentication?

    /**
     * Removes [id] so neither a completed nor an expired [PendingAuthentication] can be presented
     * again — [tallyvane.identity.application.VerifySecondFactorUseCase] calls this on every path
     * out of a pending check, not only the successful one.
     */
    public suspend fun delete(id: PendingAuthenticationId)
}
