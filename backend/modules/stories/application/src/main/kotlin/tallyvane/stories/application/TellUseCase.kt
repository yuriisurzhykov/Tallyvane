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
 * A person tells something about their work: a new story, or more of one they began.
 *
 * The words are kept first; the model is asked outside any transaction; what it heard is checked by the
 * domain against the words, and only then kept.
 */
public interface TellUseCase : UseCase {
    public suspend fun tell(account: Uuid, story: Uuid?, words: String): StoryOutcome

    @Suppress("LongParameterList") // The ports a telling touches, and the catalog its skills come from.
    public class Tell(
        private val stories: Stories,
        private val reader: Reader,
        private val listening: Listening,
        private val catalog: SkillCatalog,
        private val transactions: TransactionRunner,
        private val ids: IdGenerator,
    ) : TellUseCase {
        override suspend fun tell(account: Uuid, story: Uuid?, words: String): StoryOutcome {
            val text = UntrustedText.of(words) ?: return StoryOutcome.Failed.Empty()
            val begun = story?.let { id -> transactions.inTransaction { Verdict.Commit(stories.find(id)) } }
                ?.takeIf { it.isOf(account) }
            return if (story != null && begun == null) StoryOutcome.Failed.Missing() else told(account, begun, text)
        }

        private suspend fun told(account: Uuid, begun: Story?, text: UntrustedText): StoryOutcome {
            val current = begun ?: Story.begun(ids.next(), account)
            val utterance = listening.keep(account, current.id(), text)
            val reading = reader.read(text, account, current.missing())
            return listening.failureOf(reading) ?: heard(current, text, utterance, reading as Reading.Heard)
        }

        private suspend fun heard(current: Story, text: UntrustedText, utterance: Uuid, reading: Reading.Heard) =
            transactions.inTransaction {
                val known = catalog.skills().map { it.slug }.toSet()
                val latest = stories.find(current.id()) ?: current
                val story = latest.hear(
                    Hearing(text, utterance, known),
                    reading.claims,
                    reading.place,
                    reading.title,
                    ids,
                )
                stories.save(story)
                Verdict.Commit(StoryOutcome.Told(story))
            }

        override fun toString(): String = "Tell(stories=$stories, reader=$reader)"
    }
}
