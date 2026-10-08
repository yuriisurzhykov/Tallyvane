package tallyvane.identity.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `identity.admins`: the accounts that were given the right to administer. Rows are added by an operator's
 * script and never by the application (ADR-097).
 */
internal object AdminsTable : Table("identity.admins") {
    val accountId = uuid("account_id")
    val grantedAt = timestamp("granted_at")

    override val primaryKey = PrimaryKey(accountId)
}
