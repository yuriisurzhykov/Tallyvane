package tallyvane.sessions.application

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.DeviceName
import tallyvane.sessions.domain.SessionId

/**
 * A person gives one of their devices a name they will know it by (ADR-090).
 *
 * A browser says its kind and its system, which is not enough to tell two of the person's laptops apart;
 * the name is the one thing only they know.
 */
public interface RenameDeviceUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     * @param device The session to name.
     * @param name What to call it, as the person typed it.
     */
    public suspend fun rename(session: Secret?, device: SessionId, name: String): DeviceOutcome

    public class RenameDevice(
        private val recognition: Recognition,
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
    ) : RenameDeviceUseCase {
        override suspend fun rename(session: Secret?, device: SessionId, name: String): DeviceOutcome =
            transactions.inTransaction {
                Verdict.Commit(
                    recognition.onBehalfOf(session) { account, _ ->
                        when {
                            !DeviceName.accepts(name) -> DeviceOutcome.Failed.NameRefused()
                            sessions.rename(account.value, device, DeviceName(name)) -> DeviceOutcome.Done()
                            else -> DeviceOutcome.Failed.NoSuchDevice()
                        }
                    },
                )
            }

        override fun toString(): String = "RenameDevice($sessions)"
    }
}
