package tallyvane.sessions.domain

import kotlin.time.Duration

/**
 * Lifetimes as someone proposed them, before anyone has checked they may exist (ADR-078, ADR-079).
 *
 * The code sets the bounds and the policy sets the values: an administrator chooses how long a session may sit
 * unused, how long it may live and how long a proof stays fresh, but only between the ends [Lifetimes] sets.
 * [check] is the one way from a draft to [ClientLifetimes], and it tells every bound broken at once.
 *
 * @param client The kind of client the lifetimes are for.
 * @param idle How long a session may go unused.
 * @param absolute How long a session may live, however much it is used.
 * @param freshness How long a proof of who the person is stays fresh for a dangerous act.
 */
public class LifetimeDraft(
    private val client: ClientType,
    private val idle: Duration,
    private val absolute: Duration,
    private val freshness: Duration,
) {
    /**
     * The lifetimes this draft describes, or every bound it breaks.
     */
    public fun check(): LifetimeCheck {
        val violations = listOfNotNull(
            LifetimeViolation.IdleOutOfBounds(idle, Lifetimes.IDLE).takeUnless { idle in Lifetimes.IDLE },
            LifetimeViolation.AbsoluteOutOfBounds(absolute, Lifetimes.LONGEST).takeIf { absolute > Lifetimes.LONGEST },
            LifetimeViolation.AbsoluteBelowIdle(absolute, idle).takeIf { absolute < idle },
            LifetimeViolation.FreshnessOutOfBounds(freshness, Lifetimes.FRESHNESS)
                .takeUnless { freshness in Lifetimes.FRESHNESS },
        )
        return if (violations.isEmpty()) {
            LifetimeCheck.Passed(ClientLifetimes(client, Lifetimes(idle, absolute, freshness)))
        } else {
            LifetimeCheck.Rejected(violations)
        }
    }

    override fun toString(): String =
        "LifetimeDraft(client=$client, idle=$idle, absolute=$absolute, freshness=$freshness)"
}
