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
 * A person tells something about their work: a new story, or more of one they began.
 *
 * The model is asked outside any transaction; then one transaction keeps the words, and the facts the domain
 * found quoted in them. When the model fails, the words are kept all the same.
 */
public interface TellUseCase : UseCase {
    public suspend fun tell(account: Uuid, story: Uuid?, words: String): StoryOutcome

    public class Tell(
        private val reader: Reader,
        private val listening: Listening,
        private val catalog: SkillCatalog,
        private val ids: IdGenerator,
    ) : TellUseCase {
        override suspend fun tell(account: Uuid, story: Uuid?, words: String): StoryOutcome {
            val text = UntrustedText.of(words) ?: return StoryOutcome.Failed.Empty()
            val begun = story?.let { listening.find(it) }?.takeIf { it.isOf(account) }
            return if (story != null && begun == null) StoryOutcome.Failed.Missing() else told(account, begun, text)
        }

        private suspend fun told(account: Uuid, begun: Story?, text: UntrustedText): StoryOutcome {
            val current = begun ?: Story.begun(ids.next(), account)
            val utterance = listening.next()
            val reading = reader.read(text, account, current.missing())
            val failure = listening.failureOf(reading)
            val known = catalog.skills().map { it.slug }.toSet()
            return listening.keep(utterance, account, current, text) { latest ->
                failure ?: (reading as Reading.Heard).let {
                    StoryOutcome.Told(latest.hear(Hearing(text, utterance, known), it.claims, it.place, it.title, ids))
                }
            }
        }

        override fun toString(): String = "Tell(reader=$reader)"
    }
}
