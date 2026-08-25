package tallyvane.platform.observability.log

import tallyvane.platform.kernel.IdGenerator
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identifies one request across everything that serves it.
 *
 * Shape and vocabulary are W3C Trace Context's `trace-id`: 16 bytes as 32
 * lowercase hex characters, never all zeros. §16.6 asks for a correlation
 * identifier crossing every layer and module, and this is the value it carries.
 * The standard's shape is used rather than an opaque identifier of our own so
 * that the value stays meaningful to anything else that already speaks trace
 * context, and so that adopting real tracing later adds to this instead of
 * renaming it — see [ADR-056](../../../../../../../docs/adr/ADR-056-request-identity.md).
 *
 * Constructed from an [IdGenerator]'s UUIDv7 through [from], which keeps the
 * millisecond prefix §8.1 relies on and keeps randomness behind the port
 * (`no-ambient-random`).
 */
@JvmInline
public value class TraceId(public val value: String) {
    init {
        require(value.length == LENGTH) {
            "A trace id is $LENGTH hex characters, got ${value.length}"
        }
        require(value.all { it in DIGITS }) {
            "A trace id is lowercase hex only: $value"
        }
        require(value.any { it != '0' }) {
            "An all-zero trace id is invalid per W3C Trace Context"
        }
    }

    public companion object {
        private const val LENGTH = 32

        private const val DIGITS = "0123456789abcdef"

        /**
         * The whole identifier, since a UUID and a trace id are both 16 bytes.
         */
        @OptIn(ExperimentalUuidApi::class)
        public fun from(id: Uuid): TraceId = TraceId(id.toHexString())
    }
}
