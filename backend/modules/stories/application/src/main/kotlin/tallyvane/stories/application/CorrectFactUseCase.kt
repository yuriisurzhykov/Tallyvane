package tallyvane.stories.application

import tallyvane.catalog.contract.SkillCatalog
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.kernel.UseCase
import tallyvane.stories.application.port.Reader
import tallyvane.stories.domain.Hearing
import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

/**
 * A person says a fact is wrong, in their own words. The old fact stays, marked as replaced.
 */
public interface CorrectFactUseCase : UseCase {
    public suspend fun correct(account: Uuid, story: Uuid, fact: Uuid, words: String): StoryOutcome

    public class CorrectFact(
        private val reader: Reader,
        private val listening: Listening,
        private val catalog: SkillCatalog,
        private val ids: IdGenerator,
    ) : CorrectFactUseCase {
        override suspend fun correct(account: Uuid, story: Uuid, fact: Uuid, words: String): StoryOutcome {
            val text = UntrustedText.of(words) ?: return StoryOutcome.Failed.Empty()
            val found = listening.find(story)?.takeIf { it.isOf(account) && it.live(fact) != null }
            return if (found == null) StoryOutcome.Failed.Missing() else corrected(account, found, fact, text)
        }

        private suspend fun corrected(account: Uuid, found: Story, fact: Uuid, text: UntrustedText): StoryOutcome {
            val utterance = listening.next()
            val reading = reader.correct(checkNotNull(found.live(fact)), text, account)
            val failure = listening.failureOf(reading)
            val claim = (reading as? Reading.Heard)?.claims?.firstOrNull()
            val known = catalog.skills().map { it.slug }.toSet()
            return listening.keep(utterance, account, found, text) { latest ->
                failure ?: claim?.let { latest.correct(fact, Hearing(text, utterance, known), it, ids) }
                    ?.let { StoryOutcome.Told(it) } ?: StoryOutcome.Failed.Unheard()
            }
        }

        override fun toString(): String = "CorrectFact(reader=$reader)"
    }
}
