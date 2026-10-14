package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import tallyvane.identity.contract.AccountId
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Everything [KeptVersions] says when asked to [KeptVersions.writeTo], as lines, so a spec can write the history it
 * expects out in full, the way [VersionStory] does for one version.
 */
class HistoryStory(history: KeptVersions) {
    private val lines: List<String> = Listener().also { history.writeTo(it) }.told()

    fun told(): List<String> = lines.toList()

    override fun equals(other: Any?): Boolean = other is HistoryStory && lines == other.lines

    override fun hashCode(): Int = lines.hashCode()

    override fun toString(): String = lines.joinToString(separator = "\n", prefix = "HistoryStory(\n", postfix = "\n)")

    private class Listener : KeptVersions.Record {
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

        override fun purpose(purpose: Purpose) {
            lines += "purpose $purpose"
        }

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) {
            lines += "step ${accepts.sortedBy { it.ordinal }} $necessity"
        }

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
            lines += "limits $attemptLifetime $maxFailures $firstDelay"
        }

        fun told(): List<String> = lines.toList()
    }
}
