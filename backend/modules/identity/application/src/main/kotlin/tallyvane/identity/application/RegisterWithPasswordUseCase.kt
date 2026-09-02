package tallyvane.identity.application

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.Credential
import tallyvane.identity.domain.RegisterOutcome
import tallyvane.identity.domain.User
import tallyvane.identity.domain.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Creates a new account with a password credential — one action, per ADR-053, distinct from
 * [tallyvane.identity.application.SignInWithPasswordUseCase].
 *
 * Does not open a session. `SessionIssuer`'s own KDoc names the four paths that do — password
 * sign-in, either Google method, and second-factor verification — and registering is not a fifth:
 * signing in afterward is a separate, deliberate action a client asks for on its own.
 */
public interface RegisterWithPasswordUseCase : UseCase {
    public suspend fun register(request: RegisterWithPasswordRequest): RegisterOutcome

    /**
     * `users.insert` decides "email taken" by attempting the write, not by a `findByEmail` check
     * beforehand — see [UserRepository]'s own KDoc for why a preceding check is a race, not a
     * safeguard.
     */
    public class Register(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwordHasher: PasswordHasher,
        private val transactions: TransactionRunner,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) : RegisterWithPasswordUseCase {
        override suspend fun register(request: RegisterWithPasswordRequest): RegisterOutcome {
            val userId = UserId(ids.next())
            val now = clock.now()
            val user = User(
                id = userId,
                email = request.email,
                displayName = request.displayName,
                createdAt = now,
                disabledAt = null,
            )
            val hash = passwordHasher.hash(request.rawPassword)
            return transactions.inTransaction {
                when (users.insert(user)) {
                    UserRepository.InsertOutcome.EMAIL_TAKEN -> Verdict.Rollback(RegisterOutcome.EmailTaken)
                    UserRepository.InsertOutcome.INSERTED -> {
                        credentials.save(userId, Credential.PasswordRecord(hash))
                        Verdict.Commit(RegisterOutcome.Registered(userId))
                    }
                }
            }
        }
    }
}
