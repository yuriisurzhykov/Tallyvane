package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType

internal object AuthenticationPolicySchemesTable : Table("identity.authentication_policy_schemes") {
    val id = text("id")
    val action = text("action")
    val requiredTokens = array("required_tokens", TextColumnType())
    val assuranceRank = integer("assurance_rank")
    val enabled = bool("enabled")

    override val primaryKey = PrimaryKey(id)
}
