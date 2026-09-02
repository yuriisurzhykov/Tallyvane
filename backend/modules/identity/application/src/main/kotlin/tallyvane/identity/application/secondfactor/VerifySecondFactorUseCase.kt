package tallyvane.identity.application.secondfactor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.outcome.SecondFactorOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Fallback
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration
import tallyvane.identity.contract.UserId as ContractUserId

/**
 * Completes a [PendingAuthentication] with a second-factor code — one action, per ADR-053, for any
 * enrolled [tallyvane.identity.application.port.SecondFactorMethod]: the request's own [kind]
 * discriminator picks which one, per [PendingAuthentication.availableMethods].
 */
public interface VerifySecondFactorUseCase : UseCase {
    public suspend fun verify(request: VerifySecondFactorRequest): VerifySecondFactorOutcome

    /**
     * [PendingAuthenticationStore.delete] runs on every path out of a pending check, not only the
     * successful one — an expired or already-wrong-coded pending is removed just as eagerly as a
     * completed one, so neither can be presented again.
     */
    public class Verify internal constructor(
        private val pendingAuthentications: PendingAuthenticationStore,
        private val registry: SecondFactorMethodRegistry,
        private val sessions: SessionIssuer,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : VerifySecondFactorUseCase {
        /**
         * One [transactions.inTransaction] covers the whole method, the lookup included — see
         * `SignInWithPasswordUseCase.SignIn`'s own KDoc for why, and
         * `backend/playground/transactions/README.md` for where that was checked against a real
         * database rather than assumed.
         */
        override suspend fun verify(request: VerifySecondFactorRequest): VerifySecondFactorOutcome =
            transactions.inTransaction {
                val pending = pendingAuthentications.find(request.pendingId)
                val outcome = if (pending == null) {
                    VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.UnknownPending)
                } else if (clock.now() > pending.expiresAt) {
                    pendingAuthentications.delete(pending.id)
                    VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.Expired)
                } else {
                    checkCode(pending, request)
                }
                Verdict.Commit(outcome)
            }

        private suspend fun checkCode(
            pending: PendingAuthentication,
            request: VerifySecondFactorRequest,
        ): VerifySecondFactorOutcome {
            val method = registry.find(request.kind)
            val verified = request.kind in pending.availableMethods &&
                method != null &&
                method.verify(pending.userId, request.code)
            return if (!verified) {
                VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.WrongCode)
            } else {
                pendingAuthentications.delete(pending.id)
                val principal = Principal.User(ContractUserId(pending.userId.value))
                VerifySecondFactorOutcome.Issued(sessions.issue(principal, pending.device))
            }
        }
    }

    /**
     * Decorates [Verify] with rate limiting: counts recent wrong codes for the presented
     * [VerifySecondFactorRequest.pendingId] and refuses once [threshold] is reached within
     * [window], the same shape [tallyvane.identity.application.password.SignInWithPasswordUseCase.RateLimited]
     * already uses for a primary credential — why the read fails closed, the write fails open, and
     * both log at the point the policy is decided: `application/README.md`.
     */
    public class RateLimited(
        private val origin: VerifySecondFactorUseCase,
        private val attempts: LoginAttempts,
        private val threshold: Int,
        private val window: Duration,
    ) : VerifySecondFactorUseCase {
        override suspend fun verify(request: VerifySecondFactorRequest): VerifySecondFactorOutcome {
            val key = rateLimitKey(request.pendingId)
            val count = Fallback { attempts.failuresWithin(key, window) }
                .orRecover { failure ->
                    logger.warn("Login-attempts store unavailable; failing closed for this verification", failure)
                    threshold.toLong()
                }
            if (count >= threshold) {
                return VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.RateLimited)
            }
            val result = origin.verify(request)
            if (result == VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.WrongCode)) {
                Fallback { attempts.recordFailure(key, window) }
                    .orRecover { failure ->
                        logger.warn("Login-attempts store unavailable; could not record a wrong code", failure)
                    }
            }
            return result
        }

        public companion object {
            private const val KEY_PREFIX = "identity:verify-second-factor:"
            private val logger: Logger = LoggerFactory.getLogger(RateLimited::class.java)

            internal fun rateLimitKey(pendingId: PendingAuthenticationId): String = "$KEY_PREFIX${pendingId.value}"
        }
    }
}
