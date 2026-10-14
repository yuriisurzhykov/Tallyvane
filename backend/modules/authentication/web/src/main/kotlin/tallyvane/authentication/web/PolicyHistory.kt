package tallyvane.authentication.web

import kotlinx.serialization.Serializable
import tallyvane.authentication.application.KeptVersions
import tallyvane.authentication.application.PolicyWords
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import tallyvane.identity.contract.AccountId
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The versions of the policy of one purpose, newest first (ADR-098).
 *
 * @param purpose The word of the purpose, such as `login`.
 * @param versions Every version kept. The one in force says so in [PolicyVersionBody.inForce].
 */
@Serializable
internal class PolicyHistory(val purpose: String, val versions: List<PolicyVersionBody>) {
    /**
     * One version: its number, who made it, when and why, and its own words.
     *
     * @param madeBy The account that made it, or none for a version a migration made.
     * @param comment Why, or none for a version a migration made.
     * @param inForce Since when the version has been the one in force, by whom and why; absent for every version but
     * one.
     * @param attemptLifetimeMillis How long a sign-in attempt stays open.
     * @param maxFailures The wrong answers after which an attempt is over.
     * @param firstDelayMillis The pause after the first wrong answer.
     */
    @Serializable
    class PolicyVersionBody(
        val number: Int,
        val madeBy: String?,
        val madeAt: String,
        val comment: String?,
        val inForce: InForceBody?,
        val steps: List<StepBody>,
        val attemptLifetimeMillis: Long,
        val maxFailures: Int,
        val firstDelayMillis: Long,
    )

    /**
     * That a version is the one in force: since when, by whom and why. [by] and [reason] are absent for an
     * activation a migration made.
     */
    @Serializable
    class InForceBody(val since: String, val by: String?, val reason: String?)

    /**
     * One step: it is satisfied by any of the kinds in [accepts], and applies as [necessity] says.
     */
    @Serializable
    class StepBody(val accepts: List<String>, val necessity: String)

    /**
     * Reads what [KeptVersions] tells into the body, one version at a time.
     */
    class Reading(private val words: PolicyWords) : KeptVersions.Record {
        private val versions = mutableListOf<PolicyVersionBody>()
        private var madeBy: AccountId? = null
        private var comment: String? = null
        private var madeAt: Instant? = null
        private var inForce: InForceBody? = null
        private var number = 0
        private val steps = mutableListOf<StepBody>()

        /**
         * The body of what was told.
         *
         * @param purpose The purpose the versions are for, which an empty history does not tell.
         */
        fun of(purpose: String): PolicyHistory = PolicyHistory(purpose, versions.toList())

        override fun kept(madeBy: AccountId?, comment: String?, madeAt: Instant) {
            this.madeBy = madeBy
            this.comment = comment
            this.madeAt = madeAt
            this.inForce = null
            this.steps.clear()
        }

        override fun inForce(since: Instant, by: AccountId?, reason: String?) {
            inForce = InForceBody(since.toString(), by?.value?.toString(), reason)
        }

        override fun number(number: Int) {
            this.number = number
        }

        override fun purpose(purpose: Purpose) = Unit

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) {
            steps += StepBody(accepts.sortedBy { it.ordinal }.map(words::wordOf), words.wordOf(necessity))
        }

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
            versions += PolicyVersionBody(
                number,
                madeBy?.value?.toString(),
                checkNotNull(madeAt).toString(),
                comment,
                inForce,
                steps.toList(),
                attemptLifetime.inWholeMilliseconds,
                maxFailures,
                firstDelay.inWholeMilliseconds,
            )
        }

        override fun toString(): String = "Reading(versions=${versions.size})"
    }
}
