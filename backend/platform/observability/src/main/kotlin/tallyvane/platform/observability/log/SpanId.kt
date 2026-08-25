package tallyvane.platform.observability.log

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identifies one step within a trace: W3C Trace Context's `parent-id`, 8 bytes
 * as 16 lowercase hex characters, never all zeros.
 *
 * Until spans exist there is one per request, and its only job is to be the value
 * a downstream service names as its parent. It is separate from [TraceId] because
 * a trace id must survive a process boundary unchanged while this must not.
 */
@JvmInline
public value class SpanId(public val value: String) {
    init {
        require(value.length == LENGTH) {
            "A span id is $LENGTH hex characters, got ${value.length}"
        }
        require(value.all { it in DIGITS }) {
            "A span id is lowercase hex only: $value"
        }
        require(value.any { it != '0' }) {
            "An all-zero span id is invalid per W3C Trace Context"
        }
    }

    public companion object {
        private const val LENGTH = 16

        private const val DIGITS = "0123456789abcdef"

        /**
         * The tail of a UUID, which in UUIDv7 is its random half — the leading
         * bytes are a millisecond timestamp two requests can share.
         */
        @OptIn(ExperimentalUuidApi::class)
        public fun from(id: Uuid): SpanId = SpanId(id.toHexString().takeLast(LENGTH))
    }
}
