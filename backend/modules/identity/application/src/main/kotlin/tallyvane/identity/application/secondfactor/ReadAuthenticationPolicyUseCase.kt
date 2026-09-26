package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.UseCase

public interface ReadAuthenticationPolicyUseCase : UseCase {
    public suspend fun read(actor: UserId): AuthenticationPolicyResult

    public class Read internal constructor(private val administration: AuthenticationPolicyAdministration) :
        ReadAuthenticationPolicyUseCase {
        override suspend fun read(actor: UserId): AuthenticationPolicyResult = administration.read(actor)
    }
}
