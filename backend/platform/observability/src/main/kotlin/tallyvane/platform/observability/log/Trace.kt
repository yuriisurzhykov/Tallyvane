package tallyvane.platform.observability.log

import tallyvane.platform.kernel.IdGenerator

/**
 * The identity of one request: which trace it belongs to, and which step it is.
 *
 * A value, not a carrier. [TraceContext] is what makes it follow a coroutine.
 */
public data class Trace(val traceId: TraceId, val spanId: SpanId) {
    public companion object {
        /**
         * A trace that starts here, for a request that arrived without one.
         */
        public fun from(ids: IdGenerator): Trace = Trace(TraceId.from(ids.next()), SpanId.from(ids.next()))
    }
}
