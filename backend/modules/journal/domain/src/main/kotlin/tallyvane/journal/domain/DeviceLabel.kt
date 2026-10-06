package tallyvane.journal.domain

/**
 * The device an entry happened on, as it was then: the browser and the system in the words the API uses, whether
 * it is a phone or a tablet, and the name its person gave it.
 *
 * A copy, not a reference: renaming or signing out the device later does not change what the journal said.
 */
public class DeviceLabel(
    private val browser: String,
    private val platform: String,
    private val mobile: Boolean,
    private val name: String?,
) {
    /**
     * Whether [other] is the same kind of device: the same browser, the same system, a phone or not. The name
     * does not count, because a person can change it and a stranger can pick any.
     */
    public fun sameKindAs(other: DeviceLabel): Boolean =
        other.browser == browser && other.platform == platform && other.mobile == mobile

    /**
     * Tells [record] what the device is.
     */
    public fun writeTo(record: Record) {
        record.device(browser, platform, mobile, name)
    }

    override fun equals(other: Any?): Boolean = other is DeviceLabel && sameKindAs(other) && other.name == name

    override fun hashCode(): Int = listOf(browser, platform, mobile, name).hashCode()

    override fun toString(): String = "DeviceLabel($browser on $platform)"

    /**
     * Whoever keeps a device label, told what it is.
     */
    public fun interface Record {
        public fun device(browser: String, platform: String, mobile: Boolean, name: String?)
    }
}
