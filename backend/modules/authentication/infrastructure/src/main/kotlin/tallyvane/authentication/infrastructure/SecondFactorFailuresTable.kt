package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `authentication.second_factor_failures`: a wrong TOTP code typed for an account.
 */
internal object SecondFactorFailuresTable : Table("authentication.second_factor_failures") {
    val id = long("id").autoIncrement()
    val accountId = uuid("account_id")
    val failedAt = timestamp("failed_at")

    override val primaryKey = PrimaryKey(id)
}
