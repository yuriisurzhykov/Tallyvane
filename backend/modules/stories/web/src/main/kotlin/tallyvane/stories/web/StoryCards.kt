package tallyvane.stories.web

import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Slot
import tallyvane.stories.domain.Story
import tallyvane.stories.domain.StoryState
import kotlin.uuid.Uuid

/**
 * Collects stories as they tell themselves, into the bodies the routes answer with. Replaced facts are left out.
 */
internal class StoryCards : Story.Record {
    private val stories = mutableListOf<StoryShown>()
    private val facts = mutableListOf<FactShown>()
    private val quotes = mutableMapOf<Uuid, MutableList<String>>()
    private var head: StoryShown? = null

    fun shown(): List<StoryShown> {
        close()
        return stories.toList()
    }

    override fun story(id: Uuid, account: Uuid, title: String?, finished: Boolean, state: StoryState, missing: Slot?) {
        close()
        val asked = missing?.name?.lowercase()
        head = StoryShown(id.toString(), title, null, null, null, null, state.name.lowercase(), asked, emptyList())
    }

    override fun place(company: String?, position: String?, started: String?, ended: String?) {
        val it = checkNotNull(head)
        head = StoryShown(it.id, it.title, company, position, started, ended, it.state, it.missing, emptyList())
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
        if (replacedBy == null) {
            facts +=
                FactShown(id.toString(), kind.name.lowercase(), text, figures, technologies, skills, mine, emptyList())
        }
    }

    override fun quote(fact: Uuid, utterance: Uuid, fragment: String) {
        quotes.getOrPut(fact) { mutableListOf() } += fragment
    }

    private fun close() {
        val it = head ?: return
        val shown = facts.map { f ->
            FactShown(
                f.id,
                f.kind,
                f.text,
                f.figures,
                f.technologies,
                f.skills,
                f.mine,
                quotes[Uuid.parse(f.id)].orEmpty(),
            )
        }
        stories +=
            StoryShown(it.id, it.title, it.company, it.position, it.started, it.ended, it.state, it.missing, shown)
        facts.clear()
        quotes.clear()
        head = null
    }

    override fun toString(): String = "StoryCards(${stories.size})"
}
