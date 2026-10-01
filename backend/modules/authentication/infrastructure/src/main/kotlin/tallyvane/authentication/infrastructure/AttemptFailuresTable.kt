package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.attempt_failures`: the wrong answers of an attempt, numbered in the order the
 * attempt told them.
 */
internal object AttemptFailuresTable : Table("authentication.attempt_failures") {
    val attemptId = uuid("attempt_id")
    val position = integer("position")
    val failedAt = timestamp("failed_at")

    override val primaryKey = PrimaryKey(attemptId, position)
}
