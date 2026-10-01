package tallyvane.authentication.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Reads a [DraftCheck] that a test expects to pass, and fails the test with the violations if it
 * did not. Lets a spec build its policies the only way production can: through a draft.
 */
internal object PassedPolicy : DraftCheck.Report<SignInPolicy> {
    override fun passed(policy: SignInPolicy): SignInPolicy = policy

    override fun rejected(violations: List<Violation>): SignInPolicy =
        throw AssertionError("Expected the draft to pass, but it broke: $violations")
}

/**
 * The initial login policy of ADR-078: Google, then a second factor from whoever has one.
 */
internal fun PassedPolicy.loginAfterGoogle(): SignInPolicy = PolicyDraft(
    purpose = Purpose.Login,
    steps = listOf(
        Step(setOf(FactorKind.Google), Step.Necessity.Always),
        Step(setOf(FactorKind.Totp, FactorKind.RecoveryCode), Step.Necessity.WhenEnrolled),
    ),
    attemptLifetime = 5.minutes,
    maxFailures = 5,
    firstDelay = 1.seconds,
).check().reportTo(PassedPolicy)
