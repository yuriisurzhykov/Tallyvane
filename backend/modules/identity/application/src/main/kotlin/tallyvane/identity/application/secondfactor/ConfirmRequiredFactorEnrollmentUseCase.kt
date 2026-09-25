package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.IssuedSession
import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.contract.Principal
import tallyvane.identity.contract.UserId as ContractUserId
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Activates the required factor, consumes its restricted sign-in challenge, and completes sign-in.
 */
public interface ConfirmRequiredFactorEnrollmentUseCase : UseCase {
    public suspend fun confirm(request: Request): Outcome

    public sealed interface Outcome {
        public data class Issued(public val session: IssuedSession) : Outcome

        public data object NotConfirmed : Outcome
    }

    public data class Request(
        public val pendingId: PendingAuthenticationId,
        public val kind: SecondFactorKind,
        public val code: String,
    )

    public class Confirm internal constructor(
        private val pending: PendingAuthenticationStore,
        private val registry: SecondFactorMethodRegistry,
        private val sessions: SessionIssuer,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : ConfirmRequiredFactorEnrollmentUseCase {
        override suspend fun confirm(request: Request): Outcome = transactions.inTransaction {
            val auth = pending.find(request.pendingId)
            val method = registry.find(request.kind)?.takeIf { it.supportsEnrollment }
            val outcome = if (
                auth != null &&
                auth.requiresEnrollment &&
                clock.now() < auth.expiresAt &&
                request.kind in auth.availableMethods &&
                method?.confirmEnrollment(auth.userId, request.code) == true
            ) {
                pending.delete(request.pendingId)
                Outcome.Issued(
                    sessions.issue(
                        Principal.User(ContractUserId(auth.userId.value)),
                        auth.device,
                    ),
                )
            } else {
                Outcome.NotConfirmed
            }
            Verdict.Commit(outcome)
        }
    }
}
