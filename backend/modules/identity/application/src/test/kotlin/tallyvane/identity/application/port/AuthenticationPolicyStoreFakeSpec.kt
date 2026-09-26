package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.AuthenticationPolicy

class AuthenticationPolicyStoreFakeSpec : AuthenticationPolicyStoreConformance() {
    override fun fresh(): AuthenticationPolicyStore = MemoryAuthenticationPolicyStore()
}

private class MemoryAuthenticationPolicyStore : AuthenticationPolicyStore {
    private var policy = AuthenticationPolicy.defaults()
    override suspend fun current(): AuthenticationPolicy = policy
    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean {
        if (this.policy.version != expectedVersion) return false
        this.policy = policy
        return true
    }
}
