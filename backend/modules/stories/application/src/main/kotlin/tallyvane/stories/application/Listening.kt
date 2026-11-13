package tallyvane.stories.application

import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Utterances
import kotlin.uuid.Uuid

/**
 * The part telling and correcting share: keep the words first, in their own transaction, so they are never
 * lost to a model that failed (law 1), and turn a failed reading into an outcome.
 */
public class Listening(
    private val utterances: Utterances,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    /**
     * Keeps [words] as told by [account] about [story], and gives the new utterance's id.
     */
    public suspend fun keep(account: Uuid, story: Uuid, words: UntrustedText): Uuid {
        val id = ids.next()
        transactions.inTransaction { Verdict.Commit(utterances.keep(id, account, story, words, clock.now())) }
        return id
    }

    /**
     * The failure a reading that heard nothing ends in, or null when it heard something.
     */
    public fun failureOf(reading: Reading): StoryOutcome.Failed? = when (reading) {
        is Reading.Heard -> null
        is Reading.Unavailable -> StoryOutcome.Failed.Unavailable()
        is Reading.OverBudget -> StoryOutcome.Failed.OverBudget()
    }

    override fun toString(): String = "Listening(utterances=$utterances)"
}
