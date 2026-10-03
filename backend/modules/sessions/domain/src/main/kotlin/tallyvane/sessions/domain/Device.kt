package tallyvane.sessions.domain

/**
 * What a person would call the thing a session is on: its browser, its system, whether it is a phone or
 * a tablet, and the name they gave it, if they did (ADR-090).
 *
 * The first three are read once from the `User-Agent` of the request that began the session
 * ([UserAgent]); the name is the person's. Kept as parts so that the screen builds its own wording, in
 * its own language, and storage does not keep a sentence.
 *
 * Not a `data class`, for the reason `Session` is not one: a generated `copy()` is public.
 */
public class Device internal constructor(
    private val browser: Browser,
    private val platform: Platform,
    private val mobile: Boolean,
    private val name: DeviceName?,
) {
    /**
     * This device with the name its person gave it, which replaces any name it had.
     */
    public fun named(name: DeviceName): Device = Device(browser, platform, mobile, name)

    /**
     * Tells [record] what this device is.
     */
    public fun writeTo(record: Record) {
        record.device(browser, platform, mobile, name?.let { given -> NameText().also(given::writeTo).text() })
    }

    override fun toString(): String = "Device($browser on $platform)"

    /**
     * Whoever keeps a device, told what it is. [name] is null until its person gives one.
     */
    public interface Record {
        public fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?)
    }

    private class NameText : DeviceName.Record {
        private val told = mutableListOf<String>()

        override fun name(text: String) {
            told += text
        }

        fun text(): String = told.single()
    }

    public companion object {
        /**
         * The device storage kept, from the parts it kept.
         *
         * @throws IllegalArgumentException the name kept is not one [DeviceName] accepts.
         */
        public fun restore(browser: Browser, platform: Platform, mobile: Boolean, name: String?): Device =
            Device(browser, platform, mobile, name?.let(::DeviceName))
    }
}
