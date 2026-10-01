package tallyvane.authentication.application

import tallyvane.authentication.domain.DraftCheck
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyDraft
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import tallyvane.authentication.domain.Step
import tallyvane.authentication.domain.Violation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A policy built the only way production builds one, through a [PolicyDraft], for suites that need
 * one to keep.
 *
 * Fails the test, naming the violations, if the values given are not within the bounds: a suite
 * about storage has no business passing values the domain would refuse.
 *
 * @param steps Left out, the initial steps of ADR-078 for [purpose].
 */
class CheckedPolicy(
    private val purpose: Purpose,
    private val steps: List<Step>? = null,
    private val attemptLifetime: Duration = 5.minutes,
    private val maxFailures: Int = 5,
    private val firstDelay: Duration = 1.seconds,
) : DraftCheck.Report<SignInPolicy> {
    /**
     * The policy these values describe.
     */
    fun policy(): SignInPolicy =
        PolicyDraft(purpose, steps ?: stepsThatKeepTheFloor(), attemptLifetime, maxFailures, firstDelay)
            .check()
            .reportTo(this)

    /**
     * The initial steps of ADR-078 for the purpose, which keep the floor it sets, for a suite that
     * has no opinion about steps.
     */
    private fun stepsThatKeepTheFloor(): List<Step> {
        val google = Step(setOf(FactorKind.Google), Step.Necessity.Always)
        val secondFactor = setOf(FactorKind.Totp, FactorKind.RecoveryCode)
        return when (purpose) {
            Purpose.Registration -> listOf(google)
            Purpose.Login, Purpose.StepUp -> listOf(google, Step(secondFactor, Step.Necessity.WhenEnrolled))
            Purpose.AdminLogin -> listOf(google, Step(secondFactor, Step.Necessity.Always))
        }
    }

    override fun passed(policy: SignInPolicy): SignInPolicy = policy

    override fun rejected(violations: List<Violation>): SignInPolicy =
        throw AssertionError("Expected these values to be within the bounds, but they broke: $violations")
}
