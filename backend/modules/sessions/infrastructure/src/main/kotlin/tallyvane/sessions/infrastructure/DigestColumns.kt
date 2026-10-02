package tallyvane.sessions.infrastructure

import tallyvane.platform.kernel.Digest

/**
 * What a [Digest] tells, as the two columns it is kept in.
 */
internal class DigestColumns : Digest.Record {
    private val heard = mutableListOf<Pair<ByteArray, Int>>()

    override fun digest(bytes: ByteArray, version: Int) {
        heard += bytes to version
    }

    fun bytes(): ByteArray = heard.single().first

    fun version(): Int = heard.single().second

    override fun toString(): String = "DigestColumns"
}
