package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import kotlin.time.Duration

/**
 * Everything a [PolicyVersion] says when asked to [PolicyVersion.writeTo], as lines, so two versions
 * can be compared by what they tell, the way [AttemptStory] compares attempts.
 */
class VersionStory(version: PolicyVersion) {
    private val lines: List<String> = Listener().also { version.writeTo(it) }.told()

    /**
     * What the version told, one line per word, for a spec that writes the expectation out in full.
     */
    fun told(): List<String> = lines.toList()

    override fun equals(other: Any?): Boolean = other is VersionStory && lines == other.lines

    override fun hashCode(): Int = lines.hashCode()

    override fun toString(): String = lines.joinToString(separator = "\n", prefix = "VersionStory(\n", postfix = "\n)")

    private class Listener : PolicyVersion.Record {
        private val lines = mutableListOf<String>()

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
