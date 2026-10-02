package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Accounts
import tallyvane.identity.contract.Registration
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * A new person finishes the welcome form: a name they chose and their agreement to the privacy policy.
 *
 * Creates the account and leaves the attempt as it is: complete, and waiting to be redeemed for a
 * session. Submitting twice finds the same account.
 */
public interface RegisterUseCase : UseCase {
    public suspend fun register(attempt: Secret?, name: String, agreed: Boolean): RegisterOutcome

    public class Register(
        attempts: Attempts,
        profiles: GoogleProfiles,
        policies: ActivePolicies,
        private val accounts: Accounts,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SignInKeys,
    ) : RegisterUseCase {
        private val registrations = Registrations(attempts, profiles, policies)

        override suspend fun register(attempt: Secret?, name: String, agreed: Boolean): RegisterOutcome {
            val key = attempt?.let(keys::keyOf) ?: return RegisterOutcome.Failed.NoRegistration()
            return transactions.inTransaction {
                val now = clock.now()
                val waiting = registrations.waitingUnder(key, now)
                val outcome = when {
                    waiting == null -> RegisterOutcome.Failed.NoRegistration()
                    !agreed -> RegisterOutcome.Failed.ConsentMissing()
                    else -> accounts.register(waiting.registrantNamed(name, now)).reportTo(Told())
                }
                if (outcome is RegisterOutcome.Registered) Verdict.Commit(outcome) else Verdict.Rollback(outcome)
            }
        }

        private class Told : Registration.Report<RegisterOutcome> {
            override fun registered(account: AccountId): RegisterOutcome = RegisterOutcome.Registered()

            override fun nameRefused(): RegisterOutcome = RegisterOutcome.Failed.NameRefused()
        }
    }
}
