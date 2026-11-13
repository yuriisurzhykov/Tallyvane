package tallyvane.career.infrastructure

import org.jetbrains.exposed.v1.core.Table

internal object GoalsTable : Table("career.goals") {
    val accountId = uuid("account_id")
    val role = text("role")
    override val primaryKey = PrimaryKey(accountId)
}
