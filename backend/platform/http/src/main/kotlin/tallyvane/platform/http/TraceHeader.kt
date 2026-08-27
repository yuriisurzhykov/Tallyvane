package tallyvane.platform.http

import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.observability.log.SpanId
import tallyvane.platform.observability.log.Trace
import tallyvane.platform.observability.log.TraceId

/**
 * Reads and writes W3C Trace Context's `traceparent`, which is how one user action stays one
 * story across three processes.
 *
 * ```
 * traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
 *              ^^ ^^^^^^^^^ trace id (32 hex) ^^^^ ^ span id (16) ^ ^^ flags
 * ```
 *
 * ### An incoming header is continued, not replaced
 *
 * A valid header means some caller — the extension, the frontend — already started this trace.
 * Continuing it with the same trace id is what makes "200 of the 240 ms were not ours" a
 * readable fact instead of two unrelated log streams to line up by clock.
 *
 * The header comes from outside, so it is checked rather than trusted: 32 and 16 lowercase hex
 * digits, neither all zeros, and two hex digits of flags — [TraceId] and [SpanId] refuse anything
 * else, and anything refused starts a fresh trace instead of failing the request. The residual
 * risk is named plainly: a
 * stranger can send a trace id of their choosing and make their requests look like part of
 * someone else's trace, muddying an investigation. They gain no access and read nothing — a
 * trace id grants nothing — so the cost of accepting is log noise, and the benefit is a story
 * that crosses processes.
 *
 * ### The span is always ours
 *
 * Even when the trace continues, this process mints its own span id: an id identifies one
 * interval of work, and this is a different interval than the caller's.
 */
public class TraceHeader(private val ids: IdGenerator) {
    /**
     * @param header the incoming `traceparent`, or `null` when there is none.
     * @return the trace to run this call under: the incoming one continued, or a new one.
     */
    public fun read(header: String?): Trace = parsed(header) ?: Trace.from(ids)

    /**
     * @return the value to send back, so a client can quote it when reporting a problem.
     */
    public fun write(trace: Trace): String = "$VERSION-${trace.traceId.value}-${trace.spanId.value}-$SAMPLED"

    private fun parsed(header: String?): Trace? {
        val fields = header?.split(SEPARATOR)?.takeIf { parts -> parts.size == PARTS } ?: return null
        return if (understood(fields)) continued(fields[TRACE_ID]) else null
    }

    /**
     * Every field is checked, not only the two this class goes on to use.
     *
     * The standard is explicit about that (§3.2.2.5): if `trace-id`, `parent-id` **or**
     * `trace-flags` is invalid, a fresh header is created. It is not a formality — a caller whose
     * `parent-id` is all zeros is a caller whose tracing is broken, and honouring half of its
     * header would file this request under a trace nobody can complete.
     *
     * A first version checked the field count and the version only, so a valid trace id carried a
     * malformed `parent-id` and malformed flags straight through. The KDoc above already claimed
     * both were refused, which is how it was meant to work; the code simply never looked. Found by
     * review, confirmed by the three cases in `TraceHeaderSpec` that failed before this.
     *
     * The caller's span id is validated and then dropped — this process always mints its own, so
     * only its well-formedness is of any use here.
     */
    private fun understood(fields: List<String>): Boolean = fields[VERSION_AT] == VERSION &&
        runCatching { SpanId(fields[PARENT_ID]) }.isSuccess &&
        fields[FLAGS].length == FLAG_DIGITS &&
        fields[FLAGS].all { digit -> digit in HEX }

    /**
     * Keeps the caller's trace id and mints a span of our own. Returns `null` when the id is
     * malformed, which sends the caller's header to the bin rather than the request.
     */
    private fun continued(traceId: String): Trace? = runCatching {
        Trace(TraceId(traceId), SpanId.from(ids.next()))
    }.getOrNull()

    private companion object {
        /**
         * The only version W3C defines; anything else is not a header we understand.
         */
        const val VERSION = "00"

        const val PARTS = 4

        const val SEPARATOR = '-'

        const val VERSION_AT = 0

        const val TRACE_ID = 1

        const val PARENT_ID = 2

        const val FLAGS = 3

        const val FLAG_DIGITS = 2

        const val HEX = "0123456789abcdef"

        /**
         * Sampling is not implemented, and this says "recorded" rather than pretending to
         * decide. The flag becomes a real decision when there is somewhere to send traces.
         */
        const val SAMPLED = "01"
    }
}
