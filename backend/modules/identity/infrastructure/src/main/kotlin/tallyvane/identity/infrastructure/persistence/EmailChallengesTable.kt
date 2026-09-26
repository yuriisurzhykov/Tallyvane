package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

internal object EmailChallengesTable : Table("identity.email_challenges") {
    val id = uuid("id")
    val email = text("email")
    val purpose = text("purpose")
    val binding = text("binding")
    val hash = text("hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val resendAt = timestampWithTimeZone("resend_at")
    val remainingAttempts = integer("remaining_attempts")
    val consumed = bool("consumed")
    override val primaryKey = PrimaryKey(email, purpose, binding)
}
