package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration

/**
 * Everything a [LifetimeVersion] says when asked to [LifetimeVersion.writeTo], as lines, so two versions can be
 * compared by what they tell.
 */
class VersionStory(version: LifetimeVersion) {
    private val lines: List<String> = Listener().also { version.writeTo(it) }.told()

    /**
     * What the version told, one line per word, for a spec that writes the expectation out in full.
     */
    fun told(): List<String> = lines.toList()

    override fun equals(other: Any?): Boolean = other is VersionStory && lines == other.lines

    override fun hashCode(): Int = lines.hashCode()

    override fun toString(): String = lines.joinToString(separator = "\n", prefix = "VersionStory(\n", postfix = "\n)")

    private class Listener : LifetimeVersion.Record {
        private val lines = mutableListOf<String>()

        override fun number(number: Int) {
            lines += "number $number"
        }

        override fun client(client: ClientType) {
            lines += "client $client"
        }

        override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
            lines += "lifetimes $idle $absolute $freshness"
        }

        fun told(): List<String> = lines.toList()
    }
}
