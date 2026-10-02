package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * The welcome screen asks what to prefill: the name and the address Google gave.
 */
public interface ShowRegistrationUseCase : UseCase {
    public suspend fun show(attempt: Secret?): ShowRegistrationOutcome

    public class ShowRegistration(
        attempts: Attempts,
        profiles: GoogleProfiles,
        policies: ActivePolicies,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SignInKeys,
    ) : ShowRegistrationUseCase {
        private val registrations = Registrations(attempts, profiles, policies)

        override suspend fun show(attempt: Secret?): ShowRegistrationOutcome {
            val key = attempt?.let(keys::keyOf) ?: return ShowRegistrationOutcome.Failed.NoRegistration()
            return transactions.inTransaction {
                val waiting = registrations.waitingUnder(key, clock.now())
                Verdict.Commit(
                    waiting?.let { ShowRegistrationOutcome.Welcome(it.profile) }
                        ?: ShowRegistrationOutcome.Failed.NoRegistration(),
                )
            }
        }
    }
}
