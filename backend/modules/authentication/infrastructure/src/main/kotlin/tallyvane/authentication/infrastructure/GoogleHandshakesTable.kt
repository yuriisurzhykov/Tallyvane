package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table

/**
 * `authentication.google_handshakes`: the three secrets of a trip to Google, one row per attempt.
 */
internal object GoogleHandshakesTable : Table("authentication.google_handshakes") {
    val attemptId = uuid("attempt_id")
    val state = text("state")
    val nonce = text("nonce")
    val verifier = text("verifier")

    override val primaryKey = PrimaryKey(attemptId)
}
