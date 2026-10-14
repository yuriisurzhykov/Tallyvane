package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.sessions.domain.ClientType
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Everything [KeptLifetimes] says when asked to [KeptLifetimes.writeTo], as lines, so a spec can write the history
 * it expects out in full, the way [VersionStory] does for one version.
 */
class HistoryStory(history: KeptLifetimes) {
    private val lines: List<String> = Listener().also { history.writeTo(it) }.told()

    fun told(): List<String> = lines.toList()

    override fun equals(other: Any?): Boolean = other is HistoryStory && lines == other.lines

    override fun hashCode(): Int = lines.hashCode()

    override fun toString(): String = lines.joinToString(separator = "\n", prefix = "HistoryStory(\n", postfix = "\n)")

    private class Listener : KeptLifetimes.Record {
        private val lines = mutableListOf<String>()

        override fun kept(madeBy: AccountId?, comment: String?, madeAt: Instant) {
            lines += "kept by ${madeBy?.value} because '$comment' at $madeAt"
        }

        override fun inForce(since: Instant, by: AccountId?, reason: String?) {
            lines += "in force since $since by ${by?.value} because '$reason'"
        }

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
