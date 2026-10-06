package tallyvane.journal.contract

/**
 * What a person would call the thing they signed in on, as `sessions` knows it at that moment: the
 * browser and the system in the words the API uses for them, whether it is a phone or a tablet, and the
 * name the person gave it, if they did (ADR-095).
 *
 * The journal keeps a copy, so a later rename or sign-out of the device changes nothing it said.
 */
public class DeviceFacts(
    private val browser: String,
    private val platform: String,
    private val mobile: Boolean,
    private val name: String?,
) {
    /**
     * Tells [record] what the device is.
     */
    public fun writeTo(record: Record) {
        record.device(browser, platform, mobile, name)
    }

    override fun equals(other: Any?): Boolean = other is DeviceFacts &&
        other.browser == browser &&
        other.platform == platform &&
        other.mobile == mobile &&
        other.name == name

    override fun hashCode(): Int = listOf(browser, platform, mobile, name).hashCode()

    override fun toString(): String = "DeviceFacts($browser on $platform)"

    /**
     * Whoever keeps a device, told what it is.
     */
    public fun interface Record {
        public fun device(browser: String, platform: String, mobile: Boolean, name: String?)
    }
}
