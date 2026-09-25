package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.UseCase

public interface UpdateAuthenticationPolicyUseCase : UseCase {
    public suspend fun update(
        actor: UserId,
        expectedVersion: Long,
        rules: List<AuthenticationRule>,
        advancedAcknowledged: Boolean,
    ): AuthenticationPolicyResult

    public class Update internal constructor(private val administration: AuthenticationPolicyAdministration) :
        UpdateAuthenticationPolicyUseCase {
        override suspend fun update(
            actor: UserId,
            expectedVersion: Long,
            rules: List<AuthenticationRule>,
            advancedAcknowledged: Boolean,
        ): AuthenticationPolicyResult = administration.update(actor, expectedVersion, rules, advancedAcknowledged)
    }
}
