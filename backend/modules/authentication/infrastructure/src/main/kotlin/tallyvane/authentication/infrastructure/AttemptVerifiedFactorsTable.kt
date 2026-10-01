package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.attempt_verified_factors`: the factors an attempt verified, numbered in the order
 * the attempt told them.
 */
internal object AttemptVerifiedFactorsTable : Table("authentication.attempt_verified_factors") {
    val attemptId = uuid("attempt_id")
    val position = integer("position")
    val kind = text("kind")
    val verifiedAt = timestamp("verified_at")

    override val primaryKey = PrimaryKey(attemptId, position)
}
