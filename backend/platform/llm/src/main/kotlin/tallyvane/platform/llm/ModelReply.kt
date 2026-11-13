package tallyvane.platform.llm

/**
 * How asking the model ended. Every way it can fail is an answer, so a caller decides what a person sees.
 */
public sealed interface ModelReply {
    /**
     * The model's text, which the caller still has to read and check.
     *
     * @property remembered True when the text came from the memo and nothing was paid for it.
     */
    public class Answered(public val text: String, public val tokens: TokenCount, public val remembered: Boolean) :
        ModelReply {
        override fun toString(): String = "Answered(remembered=$remembered, $tokens)"
    }

    /**
     * The model could not be asked or would not answer: no key, the network, a refusal, a cut-off reply.
     */
    public class Unavailable(public val reason: String) : ModelReply {
        override fun toString(): String = "Unavailable($reason)"
    }

    /**
     * The account has spent what a day allows, and the call was not made.
     */
    public class OverBudget : ModelReply {
        override fun toString(): String = "OverBudget"
    }
}
