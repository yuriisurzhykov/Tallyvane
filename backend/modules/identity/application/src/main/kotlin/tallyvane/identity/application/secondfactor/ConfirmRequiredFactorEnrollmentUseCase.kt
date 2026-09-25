package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Activates the required factor and consumes its restricted sign-in challenge without issuing a session.
 */
public interface ConfirmRequiredFactorEnrollmentUseCase : UseCase {
    public suspend fun confirm(request: Request): Boolean

    public data class Request(
        public val pendingId: PendingAuthenticationId,
        public val kind: SecondFactorKind,
        public val code: String,
    )

    public class Confirm internal constructor(
        private val pending: PendingAuthenticationStore,
        private val registry: SecondFactorMethodRegistry,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : ConfirmRequiredFactorEnrollmentUseCase {
        override suspend fun confirm(request: Request): Boolean = transactions.inTransaction {
            val auth = pending.find(request.pendingId)
            val method = registry.find(request.kind)?.takeIf { it.supportsEnrollment }
            val confirmed = auth != null &&
                auth.requiresEnrollment &&
                clock.now() < auth.expiresAt &&
                request.kind in auth.availableMethods &&
                method?.confirmEnrollment(auth.userId, request.code) == true
            if (confirmed) pending.delete(request.pendingId)
            Verdict.Commit(confirmed)
        }
    }
}
