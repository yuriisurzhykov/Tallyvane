package tallyvane.sessions.application

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.SessionId

/**
 * A person signs out on one of their devices (ADR-090).
 *
 * The session is forgotten, so the next request carrying its secret is a stranger's. A session that is
 * not theirs is not found by the account it is asked under, and is answered like one that is not there.
 * The one in use may be named too: it is then the same as signing out here.
 *
 * Unlike the sign-in of a device, this asks for no fresh proof yet; ending a session only takes access away
 * from whoever holds it, and the check arrives with the second factor (slice 5, ADR-090).
 */
public interface RevokeDeviceUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     * @param device The session to end.
     */
    public suspend fun revoke(session: Secret?, device: SessionId): DeviceOutcome

    public class RevokeDevice(
        private val recognition: Recognition,
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
    ) : RevokeDeviceUseCase {
        override suspend fun revoke(session: Secret?, device: SessionId): DeviceOutcome = transactions.inTransaction {
            Verdict.Commit(
                recognition.onBehalfOf(session) { account, _ ->
                    if (sessions.revoke(account.value, device)) {
                        DeviceOutcome.Done()
                    } else {
                        DeviceOutcome.Failed.NoSuchDevice()
                    }
                },
            )
        }

        override fun toString(): String = "RevokeDevice($sessions)"
    }
}
