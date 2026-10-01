package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.attempts`: one row per sign-in attempt, holding what is fixed when it starts.
 */
internal object AttemptsTable : Table("authentication.attempts") {
    val id = uuid("id")
    val purpose = text("purpose")
    val startedAt = timestamp("started_at")

    override val primaryKey = PrimaryKey(id)
}
