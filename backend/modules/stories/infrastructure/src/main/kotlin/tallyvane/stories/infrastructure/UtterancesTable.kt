package tallyvane.stories.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object UtterancesTable : Table("stories.utterances") {
    val id = uuid("id")
    val accountId = uuid("account_id")
    val storyId = uuid("story_id")
    val words = text("words")
    val saidAt = timestamp("said_at")
    override val primaryKey = PrimaryKey(id)
}
