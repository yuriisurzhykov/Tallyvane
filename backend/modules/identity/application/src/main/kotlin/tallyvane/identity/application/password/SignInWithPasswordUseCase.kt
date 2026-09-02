package tallyvane.identity.application.password

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SignInOutcome
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
 * Checks a password credential and, if it is valid, issues a session — one action, per ADR-053,
 * distinct from [tallyvane.identity.application.password.RegisterWithPasswordUseCase].
 */
public interface SignInWithPasswordUseCase : UseCase {
    public suspend fun signIn(request: SignInWithPasswordRequest): SignInOutcome

    /**
     * "No such account" and "wrong password" both answer [AuthenticationOutcome.InvalidCredential]
     * — the design's own rule, "never reveal that the email is unknown".
     */
    public class SignIn internal constructor(
        private val users: UserRepository,
        private val credentials: CredentialRepository,
        private val passwordHasher: PasswordHasher,
        private val completer: AuthenticationCompleter,
    ) : SignInWithPasswordUseCase {
        override suspend fun signIn(request: SignInWithPasswordRequest): SignInOutcome {
            val outcome = checkCredential(request)
            return if (outcome is AuthenticationOutcome.Success) {
                completer.complete(outcome.userId, request.device)
            } else {
                SignInOutcome.NotIssued(outcome)
            }
        }

        private suspend fun checkCredential(request: SignInWithPasswordRequest): AuthenticationOutcome {
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
     * Decorates [SignIn] with rate limiting: counts recent failed sign-ins for the presented email
     * and refuses once [threshold] is reached within [window], without calling [origin] at all.
     *
     * ```
     * val limited = RateLimited(origin = SignIn(...), attempts, threshold = 5, window = 15.minutes)
     * limited.signIn(request) // -> NotIssued(RateLimited) once 5 failures happen in 15 minutes
     * ```
     *
     * Only failed attempts count toward the threshold — a successful sign-in never spends the same
     * budget a wrong password does — and a failure is recorded only after [origin] itself answers
     * [AuthenticationOutcome.InvalidCredential].
     *
     * Why the read fails closed and the write fails open when [attempts] itself is unavailable,
     * and why both log at the point the policy is decided: `application/README.md`.
     */
    public class RateLimited(
        private val origin: SignInWithPasswordUseCase,
        private val attempts: LoginAttempts,
        private val threshold: Int,
        private val window: Duration,
    ) : SignInWithPasswordUseCase {
        override suspend fun signIn(request: SignInWithPasswordRequest): SignInOutcome {
            val key = rateLimitKey(request.email)
            val count = Fallback { attempts.failuresWithin(key, window) }
                .orRecover { failure ->
                    logger.warn("Login-attempts store unavailable; failing closed for this sign-in", failure)
                    threshold.toLong()
                }
            if (count >= threshold) {
                return SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
            }
            val result = origin.signIn(request)
            if (result == SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)) {
                Fallback { attempts.recordFailure(key, window) }
                    .orRecover { failure ->
                        logger.warn("Login-attempts store unavailable; could not record a failed sign-in", failure)
                    }
            }
            return result
        }

        /**
         * The one place this string is built — `RateLimitedSpec` asks for the same value through
         * this function rather than repeating the literal, so the two cannot drift apart.
         *
         * `cache-key-is-module-prefixed` (`platform:cache/README.md`) cannot see this key: it only
         * matches a literal at the exact call site of `Counter.increment`/`Counter.count`, and
         * that call happens one layer down, in `LoginAttemptsOverCounter`, with this key arriving
         * as a parameter. Recorded as a known gap, not quietly relied on.
         */
        public companion object {
            private const val KEY_PREFIX = "identity:sign-in-password:"
            private val logger: Logger = LoggerFactory.getLogger(RateLimited::class.java)

            internal fun rateLimitKey(email: Email): String = "$KEY_PREFIX${email.value}"
        }
    }
}
