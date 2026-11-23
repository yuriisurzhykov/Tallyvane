package tallyvane.stories.application

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Stories
import tallyvane.stories.contract.Experience
import tallyvane.stories.contract.ToldFact
import tallyvane.stories.contract.ToldStory
import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Slot
import tallyvane.stories.domain.Story
import tallyvane.stories.domain.StoryState
import kotlin.uuid.Uuid

/**
 * [Experience] over the stories kept here.
 */
public class StoriesExperience(private val stories: Stories, private val transactions: TransactionRunner) :
    Experience {
    override suspend fun of(account: Uuid): List<ToldStory> = transactions.inTransaction {
        Verdict.Commit(stories.of(account).map { story -> Told().also { story.writeTo(it) }.story() })
    }

    override fun toString(): String = "StoriesExperience($stories)"

    private class Told : Story.Record {
        private var id: Uuid? = null
        private var title: String? = null
        private var missing: String? = null
        private var company: String? = null
        private var position: String? = null
        private var period: String? = null
        private val facts = mutableListOf<ToldFact>()

        fun story(): ToldStory = ToldStory(
            checkNotNull(id),
            title,
            company,
            position,
            period,
            missing,
            facts.toList(),
        )

        override fun story(
            id: Uuid,
            account: Uuid,
            title: String?,
            finished: Boolean,
            state: StoryState,
            missing: Slot?,
        ) {
            this.id = id
            this.title = title
            this.missing = missing?.name?.lowercase()
        }

        override fun place(company: String?, position: String?, started: String?, ended: String?) {
            this.company = company
            this.position = position
            period = started?.let { "$it – ${ended ?: "now"}" }
        }

        @Suppress("LongParameterList") // What a fact is.
        override fun fact(
            id: Uuid,
            kind: FactKind,
            text: String,
            figures: List<Double>,
            technologies: List<String>,
            skills: List<String>,
            mine: Boolean,
            replacedBy: Uuid?,
        ) {
            if (replacedBy == null) facts += ToldFact(kind.name.lowercase(), text, skills, mine)
        }

        override fun quote(fact: Uuid, utterance: Uuid, fragment: String) = Unit
    }
}
