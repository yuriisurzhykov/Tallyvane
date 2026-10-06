package tallyvane.journal.web

import kotlinx.serialization.Serializable

/**
 * The device an entry happened on, as it was then: the parts, so the screen words it in its own language.
 */
@Serializable
internal class DeviceShown(val browser: String, val platform: String, val mobile: Boolean, val name: String?)
