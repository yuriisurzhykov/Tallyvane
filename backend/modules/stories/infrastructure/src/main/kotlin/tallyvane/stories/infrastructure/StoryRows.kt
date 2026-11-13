package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Slot
import tallyvane.stories.domain.Story
import tallyvane.stories.domain.StoryState
import kotlin.uuid.Uuid

/**
 * A story as its rows: collects what the story tells, then writes it over whatever was kept under its id.
 */
internal class StoryRows : Story.Record {
    private var id: Uuid? = null
    private var account: Uuid? = null
    private var title: String? = null
    private var finished = false
    private val place = arrayOfNulls<String>(PLACE)
    private val facts = mutableListOf<FactRow>()
    private val quotes = mutableListOf<Triple<Uuid, Uuid, String>>()

    override fun story(id: Uuid, account: Uuid, title: String?, finished: Boolean, state: StoryState, missing: Slot?) {
        this.id = id
        this.account = account
        this.title = title
        this.finished = finished
    }

    override fun place(company: String?, position: String?, started: String?, ended: String?) {
        listOf(company, position, started, ended).forEachIndexed { index, part -> place[index] = part }
    }

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
        facts += FactRow(id, kind, text, figures, technologies, skills, mine, replacedBy)
    }

    override fun quote(fact: Uuid, utterance: Uuid, fragment: String) {
        quotes += Triple(fact, utterance, fragment)
    }

    fun write() {
        val story = checkNotNull(id)
        val exists = StoriesTable.selectAll().where { StoriesTable.id eq story }.any()
        if (exists) {
            StoriesTable.update({ StoriesTable.id eq story }) { columns(it) }
        } else {
            StoriesTable.insert {
                it[StoriesTable.id] = story
                it[accountId] = checkNotNull(account)
                columns(it)
            }
        }
        FactsTable.deleteWhere { FactsTable.storyId eq story }
        FactsTable.batchInsert(facts.withIndex()) { (index, fact) -> fact.into(this, story, index) }
        QuotesTable.batchInsert(quotes.withIndex()) { (index, quote) ->
            this[QuotesTable.factId] = quote.first
            this[QuotesTable.place] = index
            this[QuotesTable.utteranceId] = quote.second
            this[QuotesTable.fragment] = quote.third
        }
    }

    private fun columns(row: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>) {
        row[StoriesTable.title] = title
        row[StoriesTable.company] = place[0]
        row[StoriesTable.position] = place[1]
        row[StoriesTable.started] = place[2]
        row[StoriesTable.ended] = place[ENDED]
        row[StoriesTable.finished] = finished
    }

    override fun toString(): String = "StoryRows($id)"

    private companion object {
        const val PLACE = 4
        const val ENDED = 3
    }
}
