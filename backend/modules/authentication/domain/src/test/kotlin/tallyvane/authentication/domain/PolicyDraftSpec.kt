package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.domain.FactorKind.Google
import tallyvane.authentication.domain.FactorKind.RecoveryCode
import tallyvane.authentication.domain.FactorKind.Totp
import tallyvane.authentication.domain.Step.Necessity.Always
import tallyvane.authentication.domain.Step.Necessity.WhenEnrolled
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val GOOGLE = Step(setOf(Google), Always)
private val SECOND_FACTOR_IF_ENABLED = Step(setOf(Totp, RecoveryCode), WhenEnrolled)
private val SECOND_FACTOR_ALWAYS = Step(setOf(Totp, RecoveryCode), Always)

private fun draft(
    purpose: Purpose = Purpose.Login,
    steps: List<Step> = listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED),
    attemptLifetime: Duration = 5.minutes,
    maxFailures: Int = 5,
    firstDelay: Duration = 1.seconds,
) = PolicyDraft(purpose, steps, attemptLifetime, maxFailures, firstDelay)

/**
 * Writes down the outcome and every violation in words, so a test reads like the refusal an
 * administrator would see.
 */
private object Verdicts : DraftCheck.Report<List<String>>, Violation.Report<String> {
    override fun passed(policy: SignInPolicy) = listOf("passed")

    override fun rejected(violations: List<Violation>) = violations.map { it.reportTo(this) }

    override fun attemptLifetimeOutOfBounds(given: Duration, allowed: ClosedRange<Duration>) =
        "attempt lifetime $given outside $allowed"

    override fun maxFailuresOutOfBounds(given: Int, allowed: IntRange) = "max failures $given outside $allowed"

    override fun firstDelayOutOfBounds(given: Duration, allowed: ClosedRange<Duration>) =
        "first delay $given outside $allowed"

    override fun emptyStep(position: Int) = "step $position is empty"

    override fun nothingIdentifiesTheAccount() = "nothing identifies the account"

    override fun belowTheFloor(purpose: Purpose) = "$purpose below the floor"
}

private fun PolicyDraft.verdicts() = check().reportTo(Verdicts)

class PolicyDraftSpec :
    StringSpec(
        {
            "the initial policies of ADR-078 are within every bound" {
                draft(Purpose.Registration, listOf(GOOGLE)).verdicts() shouldBe listOf("passed")
                draft(Purpose.Login, listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED)).verdicts() shouldBe listOf("passed")
                draft(Purpose.AdminLogin, listOf(GOOGLE, SECOND_FACTOR_ALWAYS)).verdicts() shouldBe listOf("passed")
                draft(Purpose.StepUp, listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED)).verdicts() shouldBe listOf("passed")
            }

            // Both ends are allowed, and one unit past either end is not.
            "attempt lifetime is between one and fifteen minutes, both included" {
                draft(attemptLifetime = 1.minutes).verdicts() shouldBe listOf("passed")
                draft(attemptLifetime = 15.minutes).verdicts() shouldBe listOf("passed")
                draft(attemptLifetime = 59.seconds).verdicts() shouldBe
                    listOf("attempt lifetime 59s outside 1m..15m")
                draft(attemptLifetime = 15.minutes + 1.seconds).verdicts() shouldBe
                    listOf("attempt lifetime 15m 1s outside 1m..15m")
            }

            "between three and ten wrong answers end an attempt, both included" {
                draft(maxFailures = 3).verdicts() shouldBe listOf("passed")
                draft(maxFailures = 10).verdicts() shouldBe listOf("passed")
                draft(maxFailures = 2).verdicts() shouldBe listOf("max failures 2 outside 3..10")
                draft(maxFailures = 11).verdicts() shouldBe listOf("max failures 11 outside 3..10")
            }

            "the first pause is between one and ten seconds, both included" {
                draft(firstDelay = 1.seconds).verdicts() shouldBe listOf("passed")
                draft(firstDelay = 10.seconds).verdicts() shouldBe listOf("passed")
                draft(firstDelay = 999.milliseconds).verdicts() shouldBe listOf("first delay 999ms outside 1s..10s")
                draft(firstDelay = 10.seconds + 1.milliseconds).verdicts() shouldBe
                    listOf("first delay 10.001s outside 1s..10s")
            }

            "an empty step is named by its position, counted from one" {
                draft(Purpose.Registration, listOf(GOOGLE, Step(emptySet(), WhenEnrolled))).verdicts() shouldBe
                    listOf("step 2 is empty")
            }

            "a policy with no step every account can pass without setup is refused" {
                draft(Purpose.Registration, listOf(SECOND_FACTOR_ALWAYS)).verdicts() shouldBe
                    listOf("nothing identifies the account")
                draft(Purpose.Registration, listOf(Step(setOf(Google), WhenEnrolled))).verdicts() shouldBe
                    listOf("nothing identifies the account")
            }

            // A mistaken edit, or a captured admin API, must not turn admin sign-in into Google alone.
            "admin sign-in always demands a second factor from everyone" {
                draft(Purpose.AdminLogin, listOf(GOOGLE)).verdicts() shouldBe listOf("AdminLogin below the floor")
                draft(Purpose.AdminLogin, listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED)).verdicts() shouldBe
                    listOf("AdminLogin below the floor")
                draft(Purpose.AdminLogin, listOf(GOOGLE, Step(setOf(Totp, Google), Always))).verdicts() shouldBe
                    listOf("AdminLogin below the floor")
            }

            "registration has no factor to demand beyond Google" {
                draft(Purpose.Registration, listOf(GOOGLE)).verdicts() shouldBe listOf("passed")
            }

            // A user can only raise their own bar (ADR-078): whoever set up TOTP is asked for it,
            // both to sign in and to confirm a dangerous action, whatever an administrator saves.
            "sign-in and step-up always demand the second factor from those who set one up" {
                listOf(Purpose.Login, Purpose.StepUp).forEach { purpose ->
                    draft(purpose, listOf(GOOGLE)).verdicts() shouldBe listOf("$purpose below the floor")
                    draft(purpose, listOf(GOOGLE, Step(setOf(Totp, Google), WhenEnrolled))).verdicts() shouldBe
                        listOf("$purpose below the floor")
                    draft(purpose, listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED)).verdicts() shouldBe listOf("passed")
                }
            }

            // A form model reused after saving must not reach into the policy that passed.
            "the policy that passed does not change when the submitted collections do" {
                val factors = mutableSetOf(Totp, RecoveryCode)
                val steps = mutableListOf(GOOGLE, Step(factors, WhenEnrolled))
                val policy = draft(steps = steps).check().reportTo(PassedPolicy)
                factors.clear()
                steps.removeAt(1)
                val start = Instant.parse("2026-10-01T09:00:00Z")
                val google = Attempt(
                    Purpose.Login,
                    start,
                ).withVerified(VerifiedFactor.identifying(Google, "google-subject-1", start))

                policy.progressOf(google, Enrollment(setOf(Totp, RecoveryCode)), now = start) shouldBe
                    Progress.Awaiting(setOf(Totp, RecoveryCode))
            }

            "every violation is reported at once, not only the first" {
                draft(
                    purpose = Purpose.AdminLogin,
                    steps = listOf(Step(emptySet(), Always)),
                    attemptLifetime = Duration.ZERO,
                    maxFailures = 0,
                    firstDelay = Duration.ZERO,
                ).verdicts() shouldBe
                    listOf(
                        "attempt lifetime 0s outside 1m..15m",
                        "max failures 0 outside 3..10",
                        "first delay 0s outside 1s..10s",
                        "step 1 is empty",
                        "nothing identifies the account",
                        "AdminLogin below the floor",
                    )
            }
        },
    )
