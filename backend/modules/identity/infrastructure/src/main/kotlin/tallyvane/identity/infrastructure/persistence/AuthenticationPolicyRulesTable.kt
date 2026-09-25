package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType

internal object AuthenticationPolicyRulesTable : Table("identity.authentication_policy_rules") {
    val primaryMethod = text("primary_method")
    val enabled = bool("enabled")
    val requirement = text("requirement")
    val allowedMethods = array("allowed_methods", TextColumnType())

    override val primaryKey = PrimaryKey(primaryMethod)
}
