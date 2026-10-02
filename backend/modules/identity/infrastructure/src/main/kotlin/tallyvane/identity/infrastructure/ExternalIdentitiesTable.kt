package tallyvane.identity.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `identity.external_identities`: the names providers know an account's person by.
 */
internal object ExternalIdentitiesTable : Table("identity.external_identities") {
    val provider = text("provider")
    val subject = text("subject")
    val accountId = uuid("account_id")
    val linkedAt = timestamp("linked_at")

    override val primaryKey = PrimaryKey(provider, subject)
}
