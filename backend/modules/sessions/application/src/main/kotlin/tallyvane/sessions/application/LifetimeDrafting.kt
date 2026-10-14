package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientLifetimes
import tallyvane.sessions.domain.LifetimeCheck
import tallyvane.sessions.domain.LifetimeDraft
import tallyvane.sessions.domain.LifetimeViolation
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads [ProposedLifetimes] into a draft and checks it, saying everything wrong with it at once.
 *
 * First the words: a kind of client the API has no word for is a fault of its own. Then, if the word means
 * something, the draft is checked against the bounds the code sets and each bound it breaks is a fault too.
 */
internal class LifetimeDrafting(private val words: LifetimeWords) : ProposedLifetimes.Record {
    private var outcome: Drafted = Drafted.Refused(emptyList())

    /**
     * The lifetimes [proposal] describes, or every reason there are none.
     */
    fun of(proposal: ProposedLifetimes): Drafted {
        proposal.writeTo(this)
        return outcome
    }

    override fun lifetimes(client: String, idleMillis: Long, absoluteMillis: Long, freshnessMillis: Long) {
        val named = words.clientNamed(client)
        outcome = if (named == null) {
            Drafted.Refused(listOf(words.unknownClient(client)))
        } else {
            LifetimeDraft(named, idleMillis.milliseconds, absoluteMillis.milliseconds, freshnessMillis.milliseconds)
                .check()
                .reportTo(Checked())
        }
    }

    /**
     * Turns what the draft check found into a [Drafted].
     */
    private class Checked : LifetimeCheck.Report<Drafted> {
        override fun passed(lifetimes: ClientLifetimes): Drafted = Drafted.Passed(lifetimes)

        override fun rejected(violations: List<LifetimeViolation>): Drafted =
            Drafted.Refused(violations.map { it.reportTo(LifetimeFaults()) })
    }
}
