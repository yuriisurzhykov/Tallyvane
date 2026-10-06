package tallyvane.journal.web

import kotlinx.serialization.Serializable

/**
 * One entry in `GET /security-activity`: what happened, when, on which device when it is known, whether it is
 * the first sign-in from a device like it, and how many recovery codes were left when one was spent.
 */
@Serializable
internal class EntryShown(
    val kind: String,
    val occurredAt: String,
    val device: DeviceShown?,
    val firstFromDevice: Boolean,
    val codesLeft: Int?,
)
