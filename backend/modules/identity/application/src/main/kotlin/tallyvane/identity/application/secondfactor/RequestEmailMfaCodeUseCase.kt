package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

public interface RequestEmailMfaCodeUseCase : UseCase {
    public suspend fun request(pendingId: PendingAuthenticationId): Uuid?

    public class Request(
        private val pending: PendingAuthenticationStore,
        private val users: UserRepository,
        private val challenges: EmailChallenges,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : RequestEmailMfaCodeUseCase {
        override suspend fun request(pendingId: PendingAuthenticationId): Uuid? {
            val email = transactions.inTransaction {
                val auth = pending.find(pendingId)
                val user = auth?.takeIf {
                    it.expiresAt > clock.now() && SecondFactorKind.EMAIL_OTP in it.availableMethods
                }?.let { users.findById(it.userId) }
                if (user == null || user.disabledAt != null || !user.emailVerified) {
                    Verdict.Rollback(null)
                } else {
                    Verdict.Commit(user.email)
                }
            } ?: return null
            return challenges.issue(email, EmailChallengePurpose.MFA, pendingId.value.toString())?.id
        }
    }
}
