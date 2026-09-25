package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * Starts a factor enrollment using only the scoped, expiring challenge created by sign-in policy.
 */
public interface BeginRequiredFactorEnrollmentUseCase : UseCase {
    public suspend fun begin(request: Request): String?

    public data class Request(public val pendingId: PendingAuthenticationId, public val kind: SecondFactorKind)

    public class Begin internal constructor(
        private val pending: PendingAuthenticationStore,
        private val registry: SecondFactorMethodRegistry,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : BeginRequiredFactorEnrollmentUseCase {
        override suspend fun begin(request: Request): String? = transactions.inTransaction {
            val auth = pending.find(request.pendingId)
            val method = registry.find(request.kind)?.takeIf { it.supportsEnrollment }
            val payload = when {
                auth == null -> null
                !auth.requiresEnrollment -> null
                clock.now() >= auth.expiresAt -> null
                request.kind !in auth.availableMethods -> null
                method == null -> null
                else -> method.startEnrollment(auth.userId)
            }
            Verdict.Commit(payload)
        }
    }
}
