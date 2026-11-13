package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import tallyvane.stories.domain.Fact
import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Quote
import kotlin.uuid.Uuid

/**
 * One fact as a row. Lists are kept one item a line: names and numbers never hold a line break.
 */
@Suppress("LongParameterList") // The columns of the row.
internal class FactRow(
    private val id: Uuid,
    private val kind: FactKind,
    private val text: String,
    private val figures: List<Double>,
    private val technologies: List<String>,
    private val skills: List<String>,
    private val mine: Boolean,
    private val replacedBy: Uuid?,
) {
    fun into(row: UpdateBuilder<*>, story: Uuid, place: Int) {
        row[FactsTable.id] = id
        row[FactsTable.storyId] = story
        row[FactsTable.place] = place
        row[FactsTable.kind] = kind.name
        row[FactsTable.text] = text
        row[FactsTable.figures] = figures.joinToString(LINE)
        row[FactsTable.technologies] = technologies.joinToString(LINE) { it.replace(LINE, " ") }
        row[FactsTable.skills] = skills.joinToString(LINE)
        row[FactsTable.mine] = mine
        row[FactsTable.replacedBy] = replacedBy
    }

    override fun toString(): String = "FactRow($id)"

    companion object {
        private const val LINE = "\n"

        fun restore(row: ResultRow, quotes: List<Quote>): Fact = Fact.restore(
            row[FactsTable.id],
            FactKind.valueOf(row[FactsTable.kind]),
            row[FactsTable.text],
            quotes,
            lines(row[FactsTable.figures]).map(String::toDouble),
            lines(row[FactsTable.technologies]),
            lines(row[FactsTable.skills]),
            row[FactsTable.mine],
            row[FactsTable.replacedBy],
        )

        private fun lines(kept: String): List<String> = kept.split(LINE).filter(String::isNotEmpty)
    }
}
