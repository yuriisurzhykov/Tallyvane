package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Freshness
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.Standing
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Finds out whose request this is from the secret in its cookie: the one place a secret becomes a
 * session, a person and a verdict (ADR-079).
 *
 * Every request is recognised once by `Authenticate`; a route that acts on the person's own sessions
 * asks again through [onBehalfOf], so it learns which of them is in use without any hidden hand-over
 * between the edge and the route (ADR-090). One more lookup by a unique index, on routes used a few times
 * a month.
 *
 * The lifetimes are applied to the session as it is now, so a policy tightened this morning reaches the
 * sessions issued last week. A session found to be over is forgotten on the spot, which is all the
 * cleaning of expired sessions there is for now.
 *
 * Runs inside the caller's transaction and never opens one.
 */
public class Recognition(
    private val sessions: Sessions,
    private val lifetimes: LifetimeVersions,
    private val clock: Clock,
    private val keys: SessionKeys,
) {
    /**
     * Who [secret] speaks for, noting the use of a live session and forgetting one that is over.
     */
    public fun of(secret: Secret?): Resolution {
        val presented = secret ?: return Resolution.Anonymous()
        val key = keys.keyOf(presented)
        val now = clock.now()
        // The lifetimes are read only for a secret that names a session: a stranger costs one lookup.
        val standing = sessions.find(key)?.standingAt(now, lifetimes.active())
        return standing?.reportTo(Judging(key, now)) ?: Resolution.Lapsed()
    }

    /**
     * What [act] answers for the person [secret] speaks for and the session in use, or the reason nothing
     * can be done for them: nobody is signed in, or the session is over.
     */
    public fun onBehalfOf(secret: Secret?, act: (AccountId, SessionId) -> DeviceOutcome): DeviceOutcome =
        of(secret).reportTo(Acting(act))

    override fun toString(): String = "Recognition(sessions=$sessions)"

    /**
     * A live session is noted as used and speaks for its person; one that is over is forgotten.
     */
    private inner class Judging(private val key: Digest, private val now: Instant) : Standing.Report<Resolution> {
        override fun live(
            session: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            authenticatedAt: Instant,
            freshness: Freshness,
        ): Resolution {
            sessions.saw(key, now)
            return Resolution.SignedIn(AccountId(account), session, freshness)
        }

        override fun endedByIdleness(): Resolution = ended()

        override fun endedByAge(): Resolution = ended()

        private fun ended(): Resolution {
            sessions.forget(key)
            return Resolution.Lapsed()
        }
    }

    private class Acting(private val act: (AccountId, SessionId) -> DeviceOutcome) :
        Resolution.Report<DeviceOutcome> {
        override fun signedIn(account: AccountId, session: SessionId, freshness: Freshness): DeviceOutcome =
            act(account, session)

        override fun lapsed(): DeviceOutcome = DeviceOutcome.Failed.SessionExpired()

        override fun anonymous(): DeviceOutcome = DeviceOutcome.Failed.SignInRequired()
    }
}
