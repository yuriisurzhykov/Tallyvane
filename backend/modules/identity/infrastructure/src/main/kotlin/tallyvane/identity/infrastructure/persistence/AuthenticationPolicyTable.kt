package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table

internal object AuthenticationPolicyTable : Table("identity.authentication_policy") {
    val id = short("id")
    val version = long("version")
    val advancedAcknowledged = bool("advanced_acknowledged")

    override val primaryKey = PrimaryKey(id)
}
