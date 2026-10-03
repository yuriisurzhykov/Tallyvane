package tallyvane.sessions.web

import kotlinx.serialization.Serializable

/**
 * What `GET /devices` answers.
 */
@Serializable
internal class DevicesShown(val devices: List<DeviceShown>)
