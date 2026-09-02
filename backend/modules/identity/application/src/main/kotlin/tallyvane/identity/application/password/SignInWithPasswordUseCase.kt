package tallyvane.identity.application.password

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Fallback
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.UseCase
import kotlin.time.Duration

/**
 * Checks a password credential — one action, per ADR-053, distinct from
 * [tallyvane.identity.application.password.RegisterWithPasswordUseCase].
 */
public interface SignInWithPasswordUseCase : UseCase {
    public suspend fun signIn(request: SignInWithPasswordRequest): AuthenticationOutcome

    /**
     * "No such account" and "wrong password" both answer [AuthenticationOutcome.InvalidCredential]
     * — the design's own rule, "never reveal that the email is unknown".
     */
    public class SignIn(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwordHasher: PasswordHasher,
    ) : SignInWithPasswordUseCase {
        override suspend fun signIn(request: SignInWithPasswordRequest): AuthenticationOutcome {
            val user = users.findByEmail(request.email)
            return when {
                user == null -> AuthenticationOutcome.InvalidCredential
                user.disabledAt != null -> AuthenticationOutcome.AccountDisabled
                else -> checkPassword(user.id, request.rawPassword)
            }
        }

        private suspend fun checkPassword(userId: UserId, rawPassword: Secret): AuthenticationOutcome {
            val record = credentials.findPasswordFor(userId) ?: return AuthenticationOutcome.InvalidCredential
            return if (passwordHasher.verify(rawPassword, record.hash)) {
                AuthenticationOutcome.Success(userId)
            } else {
                AuthenticationOutcome.InvalidCredential
            }
        }
    }

    /**
     * Rate limiting for this first real consumer of [LoginAttempts] (Decorator, the same shape
     * `LlmProvider`'s `Retrying`/`Caching`/`BudgetGuarded` already take).
     *
     * Counts failures only, not every attempt: a legitimate user's own successful sign-in must not
     * spend the same budget a wrong password does. The count is checked *before* calling [origin]
     * and a failure is recorded only *after* it answers [AuthenticationOutcome.InvalidCredential].
     *
     * Fails closed when [attempts] itself is unavailable: [Fallback] recovers a failed count-check
     * to [threshold] itself, which reads as "already at the limit" and refuses the attempt, rather
     * than to a low number that would silently let every attempt through. ADR-074 names this choice
     * for the first real caller of a `platform:cache` counter, so it is not decided again here.
     * Recording a failure, by contrast, fails open — a store outage must not turn an honest "wrong
     * password" into an unrelated 500 for the caller who has nothing to do with the outage.
     */
    public class RateLimited(
        private val origin: SignInWithPasswordUseCase,
        private val attempts: LoginAttempts,
        private val threshold: Int,
        private val window: Duration,
    ) : SignInWithPasswordUseCase {
        override suspend fun signIn(request: SignInWithPasswordRequest): AuthenticationOutcome {
            val key = rateLimitKey(request.email)
            val count = Fallback { attempts.failuresWithin(key, window) }.orRecover { threshold.toLong() }
            if (count >= threshold) {
                return AuthenticationOutcome.RateLimited
            }
            val outcome = origin.signIn(request)
            if (outcome == AuthenticationOutcome.InvalidCredential) {
                Fallback { attempts.recordFailure(key, window) }.orRecover { }
            }
            return outcome
        }

        /**
         * The one place this string is built — `RateLimitedSpec` asks for the same value through
         * this function rather than repeating the literal, so the two cannot silently drift apart.
         *
         * Named honestly, not exhaustively enforced: `cache-key-is-module-prefixed`
         * (`platform:cache/README.md`) can only see a literal at the exact call site of
         * `Counter.increment`/`Counter.count`, and that call now happens one layer down, inside
         * `LoginAttemptsOverCounter`, with this key arriving as an ordinary parameter — so the
         * Konsist rule no longer sees this specific key at all. Recorded as a known gap rather than
         * quietly relied on.
         */
        public companion object {
            private const val KEY_PREFIX = "identity:sign-in-password:"

            internal fun rateLimitKey(email: Email): String = "$KEY_PREFIX${email.value}"
        }
    }
}
