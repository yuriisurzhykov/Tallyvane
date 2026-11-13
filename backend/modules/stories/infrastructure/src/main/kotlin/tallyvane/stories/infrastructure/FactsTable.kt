package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.Table

internal object FactsTable : Table("stories.facts") {
    val id = uuid("id")
    val storyId = uuid("story_id")
    val place = integer("place")
    val kind = text("kind")
    val text = text("text")
    val figures = text("figures")
    val technologies = text("technologies")
    val skills = text("skills")
    val mine = bool("mine")
    val replacedBy = uuid("replaced_by").nullable()
    override val primaryKey = PrimaryKey(id)
}
