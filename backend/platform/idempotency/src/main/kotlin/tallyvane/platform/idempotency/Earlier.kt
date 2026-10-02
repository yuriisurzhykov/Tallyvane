package tallyvane.platform.idempotency

/**
 * What the ledger knew about a claim before its request ran, which decides what the request gets.
 */
public sealed interface Earlier {
    /**
     * Nothing: this is the first.
     */
    public data object None : Earlier

    /**
     * The key was used before for a different request. The client reused it by mistake.
     */
    public data object Different : Earlier

    /**
     * The work was done and committed, and its answer is kept. Give it again.
     */
    public class Replay(private val answer: Answer) : Earlier {
        /**
         * Says the stored answer to [record].
         */
        public fun tell(record: Answer.Record) {
            answer.tell(record)
        }

        override fun toString(): String = "Replay($answer)"
    }

    /**
     * The work was done and committed, and its answer was deliberately not kept because it carried a
     * credential. It cannot be given again, and the work must not be done again.
     */
    public data object Withheld : Earlier

    /**
     * The work was done and committed, and no answer is kept yet: the first request is between its
     * commit and the moment it stores the answer, or it died there.
     */
    public data object Unanswered : Earlier
}
