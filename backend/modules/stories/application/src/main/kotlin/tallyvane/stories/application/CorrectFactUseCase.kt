package tallyvane.stories.application

import tallyvane.catalog.contract.SkillCatalog
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Reader
import tallyvane.stories.application.port.Stories
import tallyvane.stories.domain.Hearing
import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

/**
 * A person says a fact is wrong, in their own words. The old fact stays, marked as replaced.
 */
public interface CorrectFactUseCase : UseCase {
    public suspend fun correct(account: Uuid, story: Uuid, fact: Uuid, words: String): StoryOutcome

    @Suppress("LongParameterList") // The ports a correction touches, and the catalog its skills come from.
    public class CorrectFact(
        private val stories: Stories,
        private val reader: Reader,
        private val listening: Listening,
        private val catalog: SkillCatalog,
        private val transactions: TransactionRunner,
        private val ids: IdGenerator,
    ) : CorrectFactUseCase {
        override suspend fun correct(account: Uuid, story: Uuid, fact: Uuid, words: String): StoryOutcome {
            val text = UntrustedText.of(words) ?: return StoryOutcome.Failed.Empty()
            val found = transactions.inTransaction { Verdict.Commit(stories.find(story)) }
                ?.takeIf { it.isOf(account) && it.live(fact) != null }
            return if (found == null) StoryOutcome.Failed.Missing() else corrected(account, found, fact, text)
        }

        private suspend fun corrected(account: Uuid, found: Story, fact: Uuid, text: UntrustedText): StoryOutcome {
            val utterance = listening.keep(account, found.id(), text)
            val reading = reader.correct(checkNotNull(found.live(fact)), text, account)
            val claim = (reading as? Reading.Heard)?.claims?.firstOrNull()
            return listening.failureOf(reading) ?: transactions.inTransaction {
                val known = catalog.skills().map { it.slug }.toSet()
                val story = claim?.let { found.correct(fact, Hearing(text, utterance, known), it, ids) }
                story?.let(stories::save)
                Verdict.Commit(story?.let { StoryOutcome.Told(it) } ?: StoryOutcome.Failed.Unheard())
            }
        }

        override fun toString(): String = "CorrectFact(stories=$stories, reader=$reader)"
    }
}
