package tallyvane.sessions.web

import kotlinx.serialization.Serializable

/**
 * What `PUT /device-names/{id}` sends: the name the person gives the device.
 */
@Serializable
internal class DeviceNaming(val name: String)
