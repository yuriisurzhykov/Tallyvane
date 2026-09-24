package tallyvane.identity.application.email

import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.UseCase
import kotlin.uuid.Uuid

public interface RequestPasswordResetUseCase : UseCase {
    public suspend fun request(email: Email): Uuid?

    public class Send(private val challenges: EmailChallenges) : RequestPasswordResetUseCase {
        override suspend fun request(email: Email): Uuid? = challenges.issue(
            email,
            tallyvane.identity.domain.email.EmailChallengePurpose.PASSWORD_RESET,
        )?.id
    }
}
