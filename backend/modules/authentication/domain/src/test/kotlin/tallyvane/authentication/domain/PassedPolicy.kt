package tallyvane.authentication.domain

/**
 * Reads a [DraftCheck] that a test expects to pass, and fails the test with the violations if it
 * did not. Lets a spec build its policies the only way production can: through a draft.
 */
internal object PassedPolicy : DraftCheck.Report<SignInPolicy> {
    override fun passed(policy: SignInPolicy): SignInPolicy = policy

    override fun rejected(violations: List<Violation>): SignInPolicy =
        throw AssertionError("Expected the draft to pass, but it broke: $violations")
}
