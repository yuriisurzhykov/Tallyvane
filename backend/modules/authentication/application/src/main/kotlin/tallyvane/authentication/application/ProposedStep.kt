package tallyvane.authentication.application

/**
 * One step of a policy as the client wrote it: the words of the kinds that satisfy it and of when it applies.
 * Whether the words mean anything is for the use case to say.
 */
public class ProposedStep(private val accepts: List<String>, private val necessity: String) {
    /**
     * Tells [record] the words.
     */
    public fun writeTo(record: ProposedPolicy.Record) {
        record.step(accepts, necessity)
    }

    override fun toString(): String = "ProposedStep(accepts=$accepts, necessity=$necessity)"
}
