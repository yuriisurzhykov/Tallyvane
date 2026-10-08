package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * The page asks a sign-in, or a confirmation, where it stands: which factor it wants next, whether a
 * pause is running, whether it is over.
 *
 * Answers from the policy in force and the account as they are now, so a second step that began to apply
 * since the last request shows up. Changes nothing.
 */
public interface ShowSignInUseCase : UseCase {
    /**
     * @param attempt The secret from the browser's `__Host-attempt` cookie, or null when it sent none.
     */
    public suspend fun show(attempt: Secret?): SignInShown

    public class ShowSignIn(
        private val attempts: Attempts,
        private val policies: ActivePolicies,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SignInKeys,
    ) : ShowSignInUseCase {
        override suspend fun show(attempt: Secret?): SignInShown {
            val key = attempt?.let(keys::keyOf) ?: return SignInShown.Failed.NoSignIn()
            return transactions.inTransaction {
                val found = attempts.find(key)
                val purpose = found?.let { kept -> PURPOSES.firstOrNull(kept::isFor) }
                Verdict.Commit(
                    if (found == null || purpose == null) {
                        SignInShown.Failed.NoSignIn()
                    } else {
                        clock.now().let { now -> SignInShown.Shown(policies.progressOf(found, purpose, now), now) }
                    },
                )
            }
        }

        private companion object {
            /**
             * The purposes a page follows step by step: signing in (to the console or the administrators'
             * site), and confirming a dangerous act. A registration has its own screen.
             */
            val PURPOSES = listOf(Purpose.Login, Purpose.AdminLogin, Purpose.StepUp)
        }
    }
}
