package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.AuthenticationPolicy

/**
 * Versioned authentication policy persistence. Calls join the caller's transaction.
 */
public interface AuthenticationPolicyStore {
    public suspend fun current(): AuthenticationPolicy?

    /**
     * Atomically replaces the current version iff it still equals [expectedVersion].*/
    public suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean
}
