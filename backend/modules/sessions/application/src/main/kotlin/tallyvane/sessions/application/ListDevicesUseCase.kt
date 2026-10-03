package tallyvane.sessions.application

import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.Standing
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A person looks at the devices they are signed in on (ADR-090).
 *
 * Only live sessions are listed: one past its lifetimes under the policy in force is not a device they
 * are signed in on, whether or not anything has forgotten it yet. Listing writes nothing.
 */
public interface ListDevicesUseCase : UseCase {
    /**
     * @param session The secret from the browser's `__Host-session` cookie, or null when it sent none.
     * @return [DeviceOutcome.Listed], or why not.
     */
    public suspend fun list(session: Secret?): DeviceOutcome

    public class ListDevices(
        private val recognition: Recognition,
        private val sessions: Sessions,
        private val lifetimes: LifetimeVersions,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : ListDevicesUseCase {
        override suspend fun list(session: Secret?): DeviceOutcome = transactions.inTransaction {
            Verdict.Commit(
                recognition.onBehalfOf(session) { account, current ->
                    val now = clock.now()
                    val rules = lifetimes.active()
                    val live = sessions.ofAccount(account.value).filter {
                        it.standingAt(now, rules).reportTo(Liveness())
                    }
                    DeviceOutcome.Listed(live, current)
                },
            )
        }

        /**
         * Whether a session still stands.
         */
        private class Liveness : Standing.Report<Boolean> {
            override fun live(session: SessionId, account: Uuid, factors: Set<Factor>, authenticatedAt: Instant) = true

            override fun endedByIdleness() = false

            override fun endedByAge() = false
        }

        override fun toString(): String = "ListDevices($sessions)"
    }
}
