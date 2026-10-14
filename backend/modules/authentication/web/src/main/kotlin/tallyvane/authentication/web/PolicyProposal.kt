package tallyvane.authentication.web

import kotlinx.serialization.Serializable
import tallyvane.authentication.application.ProposedPolicy
import tallyvane.authentication.application.ProposedStep

/**
 * A new version of a policy as the administrator wrote it (ADR-098).
 *
 * @param purpose The word of the purpose, such as `login`.
 * @param steps The steps in the order they apply.
 * @param attemptLifetimeMillis How long a sign-in attempt stays open, in milliseconds.
 * @param maxFailures The wrong answers after which an attempt is over.
 * @param firstDelayMillis The pause after the first wrong answer, in milliseconds; each next one doubles it.
 * @param comment Why, in the administrator's words.
 */
@Serializable
internal class PolicyProposal(
    val purpose: String,
    val steps: List<StepProposal>,
    val attemptLifetimeMillis: Long,
    val maxFailures: Int,
    val firstDelayMillis: Long,
    val comment: String,
) {
    /**
     * The policy these words describe, for the use case to check.
     */
    fun policy(): ProposedPolicy = ProposedPolicy(
        purpose,
        steps.map { ProposedStep(it.accepts, it.necessity) },
        attemptLifetimeMillis,
        maxFailures,
        firstDelayMillis,
    )

    /**
     * One step: satisfied by any of the factors in [accepts] (`google`, `totp`, `recovery_code`), and applying as
     * [necessity] says (`always` or `when_enrolled`).
     */
    @Serializable
    class StepProposal(val accepts: List<String>, val necessity: String)
}
