package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `platform.idempotency_keys`: the requests already seen, and what each was answered with (ADR-086).
 */
internal object IdempotencyKeysTable : Table("platform.idempotency_keys") {
    val owner = text("owner")
    val key = uuid("key")
    val fingerprint = binary("fingerprint")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val outcome = text("outcome").nullable()
    val status = short("status").nullable()
    val contentType = text("content_type").nullable()
    val body = binary("body").nullable()

    override val primaryKey = PrimaryKey(owner, key)
}
