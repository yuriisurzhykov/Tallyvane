package tallyvane.platform.idempotency

/**
 * What a request was answered with, kept so that its repeat can be answered the same.
 *
 * A status, a content type and a body, and nothing else: never a header. A header is where a cookie, a
 * token or a trace goes, and each of those would be wrong to hand to a second request. The price is
 * that a route's answer must carry everything the client needs in its body (ADR-086).
 *
 * Its bytes reach a reader only through [tell], so whoever stores an answer and whoever replays one
 * hear the same three facts from the same place, and neither can change what is kept.
 */
public class Answer(private val status: Int, private val contentType: String?, body: ByteArray) {
    private val body: ByteArray = body.copyOf()

    /**
     * Says the answer's three facts to [record], once.
     */
    public fun tell(record: Record) {
        record.answered(status, contentType, body.copyOf())
    }

    override fun toString(): String = "Answer(status=$status, contentType=$contentType, bytes=${body.size})"

    /**
     * Whoever listens to an [Answer]: storage turns it into a row, the edge into a response.
     */
    public interface Record {
        public fun answered(status: Int, contentType: String?, body: ByteArray)
    }
}
