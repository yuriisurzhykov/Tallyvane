package tallyvane.sessions.application

import tallyvane.platform.kernel.Clock
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Freshness
import tallyvane.sessions.domain.LifetimeVersion
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.Standing
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Which sessions a version of the lifetimes would end if it were put in force now (ADR-098).
 *
 * The judge is the domain's own: each session is asked whether it stands under the rules in force and under the
 * same rules with the version in place of its kind of client's, so a preview cannot say anything a request would
 * not. Lifetimes are applied to every request and not stored on the session, so the sessions that are over under
 * the version are over at their next one.
 *
 * Runs inside the caller's transaction, as the ports it reads do.
 */
public class Endings(
    private val sessions: Sessions,
    private val versions: LifetimeVersions,
    private val clock: Clock,
) {
    /**
     * What [version] would do to the sessions of its kind of client.
     */
    internal fun of(version: LifetimeVersion): LifetimeConsequences {
        val now = clock.now()
        val rules = versions.active()
        val after = version.beside(rules)
        val live = sessions.ofClient(Clients().of(version)).filter { it.standingAt(now, rules).reportTo(Liveness()) }
        return LifetimeConsequences(live.size, live.count { !it.standingAt(now, after).reportTo(Liveness()) })
    }

    override fun toString(): String = "Endings"

    /**
     * Whether a session still stands.
     */
    private class Liveness : Standing.Report<Boolean> {
        override fun live(
            session: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            authenticatedAt: Instant,
            freshness: Freshness,
        ) = true

        override fun endedByIdleness() = false

        override fun endedByAge() = false
    }
}
