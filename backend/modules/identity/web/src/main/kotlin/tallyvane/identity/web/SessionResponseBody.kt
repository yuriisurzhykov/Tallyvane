package tallyvane.identity.web

import kotlinx.serialization.Serializable
import tallyvane.identity.domain.session.Session

@Serializable
internal data class SessionResponseBody(
    val id: String,
    val device: String,
    val createdAt: String,
    val lastUsedAt: String,
    val revokedAt: String? = null,
) {
    companion object {
        fun of(session: Session): SessionResponseBody = SessionResponseBody(
            id = session.id.value.toString(),
            device = session.device.value,
            createdAt = session.createdAt.toString(),
            lastUsedAt = session.lastUsedAt.toString(),
            revokedAt = session.revokedAt?.toString(),
        )
    }
}
