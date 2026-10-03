package tallyvane.authentication.application

import tallyvane.authentication.application.port.SeedSource
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * A signed-in person begins to turn TOTP on (ADR-093).
 *
 * Creates a pending enrolment with a new seed and tells its key once. A pending enrolment protects
 * nothing: it counts only after the first code is typed, so a person who walks away leaves their account
 * as it was. Begun again, it starts over with another seed. A route calls this only for someone who
 * proved who they are recently (ADR-092), since turning a second factor on is itself a way to lock the
 * owner out.
 */
public interface BeginTotpUseCase : UseCase {
    public suspend fun begin(account: AccountId): TotpBegun

    /**
     * @param issuer Who the authenticator app says the code is for, such as `Tallyvane`.
     */
    public class BeginTotp(
        private val enrollments: TotpEnrollments,
        private val seeds: SeedSource,
        private val issuer: String,
        private val transactions: TransactionRunner,
    ) : BeginTotpUseCase {
        override suspend fun begin(account: AccountId): TotpBegun = transactions.inTransaction {
            if (enrollments.lock(account)?.isActive() == true) {
                Verdict.Rollback(TotpBegun.Failed.AlreadyActive())
            } else {
                val fresh = TotpEnrollment.begin(seeds.next())
                enrollments.keep(account, fresh)
                val told = mutableListOf<Pair<Secret, Secret>>()
                fresh.provision(issuer) { key, uri -> told += key to uri }
                Verdict.Commit(TotpBegun.Started(told.single().first, told.single().second))
            }
        }
    }
}
