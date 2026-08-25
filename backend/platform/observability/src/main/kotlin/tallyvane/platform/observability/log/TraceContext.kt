package tallyvane.platform.observability.log

import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Makes a [Trace] follow a coroutine, and mirrors it into slf4j's MDC so every
 * log line carries it without being passed one.
 *
 * MDC is thread-local, and a coroutine resumes on whatever thread its dispatcher
 * hands it, so a value put there once is gone after the first suspension — no
 * error, just an identifier missing from the rest of the request's log. This
 * element closes that gap: it writes the two keys as the coroutine takes a
 * thread and puts back whatever was there as it leaves, so nesting and
 * concurrent coroutines on a shared pool cannot see each other's identity.
 *
 * Usage:
 *
 * ```
 * withContext(TraceContext(Trace.from(ids))) { handle(request) }
 * ```
 *
 * `kotlinx-coroutines-slf4j`'s `MDCContext` does the same for the MDC map as a
 * whole. This carries one typed value instead, which is both what §16.6 asks for
 * and readable as a `Trace` from code that needs the identifier rather than a
 * log line — see [ADR-056](../../../../../../../docs/adr/ADR-056-request-identity.md).
 */
public class TraceContext(public val trace: Trace) :
    AbstractCoroutineContextElement(Key),
    ThreadContextElement<TraceContext.Previous> {

    override fun updateThreadContext(context: CoroutineContext): Previous {
        val previous = Previous(MDC.get(TRACE_ID), MDC.get(SPAN_ID))
        MDC.put(TRACE_ID, trace.traceId.value)
        MDC.put(SPAN_ID, trace.spanId.value)
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Previous) {
        replace(TRACE_ID, oldState.traceId)
        replace(SPAN_ID, oldState.spanId)
    }

    private fun replace(key: String, value: String?) {
        value?.let { MDC.put(key, it) } ?: MDC.remove(key)
    }

    /**
     * What the thread's MDC held before this element claimed it. Absent keys are
     * `null`, which restores as removal rather than as an empty string.
     */
    public class Previous internal constructor(internal val traceId: String?, internal val spanId: String?)

    public companion object Key : CoroutineContext.Key<TraceContext> {
        private const val TRACE_ID = "trace_id"

        private const val SPAN_ID = "span_id"

        /**
         * The identity of the request this coroutine serves, or `null` outside one.
         */
        public suspend fun current(): Trace? = coroutineContext[Key]?.trace
    }
}
