package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `sessions.sessions`: one row per session, found by the digest of the secret its browser holds.
 */
internal object SessionsTable : Table("sessions.sessions") {
    val id = uuid("id")
    val secretDigest = binary("secret_digest")
    val pepperVersion = integer("pepper_version")
    val accountId = uuid("account_id")
    val authenticatedAt = timestamp("authenticated_at")
    val confirmedAt = timestamp("confirmed_at")
    val lastActiveAt = timestamp("last_active_at")
    val clientType = text("client_type")
    val deviceBrowser = text("device_browser")
    val devicePlatform = text("device_platform")
    val deviceMobile = bool("device_mobile")
    val deviceName = text("device_name").nullable()

    override val primaryKey = PrimaryKey(id)
}
