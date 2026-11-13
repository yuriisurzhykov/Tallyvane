package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.stories.application.port.Stories
import tallyvane.stories.domain.Place
import tallyvane.stories.domain.Quote
import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

internal class PostgresStories : Stories {
    override fun find(id: Uuid): Story? =
        restored(StoriesTable.selectAll().where { StoriesTable.id eq id }.toList()).singleOrNull()

    override fun of(account: Uuid): List<Story> = restored(
        StoriesTable.selectAll()
            .where { StoriesTable.accountId eq account }
            .orderBy(StoriesTable.shelf, SortOrder.DESC)
            .limit(MOST)
            .toList(),
    )

    override fun save(story: Story) {
        StoryRows().also { story.writeTo(it) }.write()
    }

    private fun restored(rows: List<ResultRow>): List<Story> {
        val ids = rows.map { it[StoriesTable.id] }
        val facts = if (ids.isEmpty()) {
            emptyList()
        } else {
            FactsTable.selectAll()
                .where { FactsTable.storyId inList ids }
                .orderBy(FactsTable.place)
                .toList()
        }
        val factIds = facts.map { it[FactsTable.id] }
        val quotes = if (factIds.isEmpty()) {
            emptyMap()
        } else {
            QuotesTable.selectAll()
                .where { QuotesTable.factId inList factIds }
                .orderBy(QuotesTable.place)
                .groupBy({
                    it[QuotesTable.factId]
                }) { Quote.restore(it[QuotesTable.utteranceId], it[QuotesTable.fragment]) }
        }
        val byStory = facts.groupBy({
            it[FactsTable.storyId]
        }) { FactRow.restore(it, quotes[it[FactsTable.id]].orEmpty()) }
        return rows.map { row ->
            Story.restore(
                row[StoriesTable.id],
                row[StoriesTable.accountId],
                Place(
                    row[StoriesTable.company],
                    row[StoriesTable.position],
                    row[StoriesTable.started],
                    row[StoriesTable.ended],
                ),
                row[StoriesTable.title],
                byStory[row[StoriesTable.id]].orEmpty(),
                row[StoriesTable.finished],
            )
        }
    }

    override fun toString(): String = "PostgresStories(schema=stories)"

    private companion object {
        const val MOST = 200
    }
}
