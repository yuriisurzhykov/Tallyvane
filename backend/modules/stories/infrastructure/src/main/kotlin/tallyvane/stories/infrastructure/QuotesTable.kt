package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.Table

internal object QuotesTable : Table("stories.quotes") {
    val factId = uuid("fact_id")
    val place = integer("place")
    val utteranceId = uuid("utterance_id")
    val fragment = text("fragment")
    override val primaryKey = PrimaryKey(factId, place)
}
