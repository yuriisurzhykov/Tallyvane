package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.UseCase
import kotlin.uuid.Uuid

public interface RequestAuthenticationActionEmailCodeUseCase : UseCase {
    public suspend fun request(
        userId: UserId,
        sessionId: SessionId,
        action: AuthenticationAction,
        kind: AuthenticationTokenKind,
    ): Uuid?
}
