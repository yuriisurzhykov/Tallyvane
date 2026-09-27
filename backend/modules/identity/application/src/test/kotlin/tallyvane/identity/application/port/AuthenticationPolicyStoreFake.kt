package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.AuthenticationPolicy

internal class AuthenticationPolicyStoreFake(var policy: AuthenticationPolicy? = AuthenticationPolicy.defaults()) :
    AuthenticationPolicyStore {
    override suspend fun current(): AuthenticationPolicy? = policy

    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean {
        if (this.policy?.version != expectedVersion) return false
        this.policy = policy
        return true
    }
}
