package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.policy_versions`: the numbers of one version of one purpose's policy.
 */
internal object PolicyVersionsTable : Table("authentication.policy_versions") {
    val purpose = text("purpose")
    val number = integer("number")
    val attemptLifetimeMillis = long("attempt_lifetime_millis")
    val maxFailures = integer("max_failures")
    val firstDelayMillis = long("first_delay_millis")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(purpose, number)
}
