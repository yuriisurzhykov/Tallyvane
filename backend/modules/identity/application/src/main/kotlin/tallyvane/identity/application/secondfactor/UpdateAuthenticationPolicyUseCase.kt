package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.secondfactor.AuthenticationScheme
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.UseCase

public interface UpdateAuthenticationPolicyUseCase : UseCase {
    public suspend fun update(
        actor: UserId,
        expectedVersion: Long,
        change: Change,
        advancedAcknowledged: Boolean,
    ): AuthenticationPolicyResult

    public sealed interface Change {
        public data class Rules(public val value: List<AuthenticationRule>) : Change
        public data class Schemes(public val value: List<AuthenticationScheme>) : Change
    }

    public class Update internal constructor(private val administration: AuthenticationPolicyAdministration) :
        UpdateAuthenticationPolicyUseCase {
        override suspend fun update(
            actor: UserId,
            expectedVersion: Long,
            change: Change,
            advancedAcknowledged: Boolean,
        ): AuthenticationPolicyResult = when (change) {
            is Change.Rules -> administration.update(actor, expectedVersion, change.value, advancedAcknowledged)
            is Change.Schemes -> administration.updateSchemes(
                actor,
                expectedVersion,
                change.value,
                advancedAcknowledged,
            )
        }
    }
}
