package tallyvane.stories.application

import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Stories
import tallyvane.stories.application.port.Utterances
import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

/**
 * The part telling and correcting share, shaped by "one request is one transaction" (ADR-086): a read before
 * the model rolls back, the model is asked outside any transaction, and then a single transaction keeps the
 * words, whatever the model answered (law 1), together with the story the words changed, if any.
 */
public class Listening(
    private val utterances: Utterances,
    private val stories: Stories,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    /**
     * The story [id], read without committing anything.
     */
    public suspend fun find(id: Uuid): Story? = transactions.inTransaction { Verdict.Rollback(stories.find(id)) }

    /**
     * Keeps [words] as the utterance [utterance] of [account] about [story], and then whatever [change] makes
     * of the latest kept version of the story (null: the story stays as it was), in one transaction.
     */
    public suspend fun keep(
        utterance: Uuid,
        account: Uuid,
        story: Story,
        words: UntrustedText,
        change: (Story) -> StoryOutcome,
    ): StoryOutcome = transactions.inTransaction {
        utterances.keep(utterance, account, story.id(), words, clock.now())
        val outcome = change(stories.find(story.id()) ?: story)
        if (outcome is StoryOutcome.Told) outcome.saveTo(stories)
        Verdict.Commit(outcome)
    }

    /**
     * A fresh utterance id.
     */
    public fun next(): Uuid = ids.next()

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
