package tallyvane.platform.kernel

/**
 * What is kept of a [Secret]: its keyed hash and the version of the key that made it.
 *
 * Not a secret itself, and still not something to print: `toString` names the version only, so a log
 * line cannot become a list of lookups to try.
 *
 * Its bytes leave through [writeTo] and nothing else, which is how storage keeps a digest without
 * anyone being able to ask one for its contents in passing.
 */
public class Digest(bytes: ByteArray, private val version: Int) {
    private val bytes = bytes.copyOf()

    init {
        require(this.bytes.isNotEmpty()) { "A digest of no bytes matches nothing and finds everything." }
        require(version >= 1) { "A pepper version counts from 1, but was given $version." }
    }

    /**
     * Tells [record] the bytes and the pepper version, for the one place that has to store them or
     * look them up.
     */
    public fun writeTo(record: Record) {
        record.digest(bytes.copyOf(), version)
    }

    override fun equals(other: Any?): Boolean =
        other is Digest && other.version == version && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + version

    override fun toString(): String = "Digest(version=$version)"

    /**
     * Whoever keeps a digest.
     */
    public fun interface Record {
        /**
         * The digest is [bytes], made with pepper version [version]. Each call gets its own copy.
         */
        public fun digest(bytes: ByteArray, version: Int)
    }
}
