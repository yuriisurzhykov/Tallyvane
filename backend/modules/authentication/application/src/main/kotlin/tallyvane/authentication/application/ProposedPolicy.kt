package tallyvane.authentication.application

/**
 * A policy as the client wrote it, in the words of the API and before anyone has checked that it means anything
 * (ADR-098). [AddPolicyVersionUseCase] turns it into a draft, and the draft is checked against the bounds.
 *
 * @param purpose The word of the purpose, such as `login`.
 * @param steps The steps in the order they apply.
 * @param attemptLifetimeMillis How long an attempt stays open, in milliseconds.
 * @param maxFailures The wrong answers after which an attempt is over.
 * @param firstDelayMillis The pause after the first wrong answer, in milliseconds.
 */
public class ProposedPolicy(
    private val purpose: String,
    private val steps: List<ProposedStep>,
    private val attemptLifetimeMillis: Long,
    private val maxFailures: Int,
    private val firstDelayMillis: Long,
) {
    /**
     * Tells [record] the policy's own words, then each step in order.
     */
    public fun writeTo(record: Record) {
        record.policy(purpose, attemptLifetimeMillis, maxFailures, firstDelayMillis)
        steps.forEach { it.writeTo(record) }
    }

    override fun toString(): String = "ProposedPolicy(purpose=$purpose, steps=$steps)"

    /**
     * What a proposal tells whoever reads it.
     */
    public interface Record {
        /**
         * The policy is for the purpose [purpose], and has these limits. Always the first thing said.
         */
        public fun policy(purpose: String, attemptLifetimeMillis: Long, maxFailures: Int, firstDelayMillis: Long)

        /**
         * The next step, in the order they apply, is satisfied by any of the kinds [accepts] names and applies as
         * [necessity] says.
         */
        public fun step(accepts: List<String>, necessity: String)
    }
}
