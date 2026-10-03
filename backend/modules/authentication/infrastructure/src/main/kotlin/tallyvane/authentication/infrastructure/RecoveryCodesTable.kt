package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.recovery_codes`: the recovery codes of an account, as digests, in the order issued.
 */
internal object RecoveryCodesTable : Table("authentication.recovery_codes") {
    val accountId = uuid("account_id")
    val position = integer("position")
    val digest = binary("digest")
    val pepperVersion = integer("pepper_version")
    val spentAt = timestamp("spent_at").nullable()

    override val primaryKey = PrimaryKey(accountId, position)
}
