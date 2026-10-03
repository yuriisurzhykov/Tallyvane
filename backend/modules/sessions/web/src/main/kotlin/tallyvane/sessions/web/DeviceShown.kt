package tallyvane.sessions.web

import kotlinx.serialization.Serializable

/**
 * One device in `GET /devices`: what it is as parts, so the screen words it in its own language, when it
 * began and was last used, and whether it is the one asking.
 */
@Serializable
internal class DeviceShown(
    val id: String,
    val browser: String,
    val platform: String,
    val mobile: Boolean,
    val name: String?,
    val signedInAt: String,
    val lastActiveAt: String,
    val current: Boolean,
)
