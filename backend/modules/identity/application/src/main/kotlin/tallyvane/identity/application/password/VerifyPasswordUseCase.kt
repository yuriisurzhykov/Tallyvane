package tallyvane.identity.application.password

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.UseCase

public interface VerifyPasswordUseCase : UseCase {
    public suspend fun verify(userId: UserId, password: Secret): Boolean

    public class Verify(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwords: PasswordHasher,
    ) : VerifyPasswordUseCase {
        override suspend fun verify(userId: UserId, password: Secret): Boolean {
            val user = users.findById(userId)
            val passwordRecord = credentials.findPasswordFor(userId)
            return user != null &&
                user.disabledAt == null &&
                user.emailVerified &&
                passwordRecord?.let { passwords.verify(password, it.hash) } == true
        }
    }
}
