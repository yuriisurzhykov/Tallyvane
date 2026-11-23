package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.Table

internal object StoriesTable : Table("stories.stories") {
    val id = uuid("id")
    val accountId = uuid("account_id")
    val title = text("title").nullable()
    val company = text("company").nullable()
    val position = text("position").nullable()
    val started = text("started").nullable()
    val ended = text("ended").nullable()
    val finished = bool("finished")
    val shelf = long("shelf").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}
