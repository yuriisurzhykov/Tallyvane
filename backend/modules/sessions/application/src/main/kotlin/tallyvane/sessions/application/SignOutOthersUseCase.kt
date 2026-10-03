package tallyvane.sessions.application

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions

/**
 * A person signs out everywhere but here (ADR-090).
 *
 * Every other session of theirs is forgotten in one statement; the one in use stays. Like signing out
 * on one device, it asks for no fresh proof yet (slice 5).
 */
public interface SignOutOthersUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     */
    public suspend fun signOutOthers(session: Secret?): DeviceOutcome

    public class SignOutOthers(
        private val recognition: Recognition,
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
    ) : SignOutOthersUseCase {
        override suspend fun signOutOthers(session: Secret?): DeviceOutcome = transactions.inTransaction {
            Verdict.Commit(
                recognition.onBehalfOf(session) { account, current ->
                    sessions.revokeOthers(account.value, current)
                    DeviceOutcome.Done()
                },
            )
        }

        override fun toString(): String = "SignOutOthers($sessions)"
    }
}
