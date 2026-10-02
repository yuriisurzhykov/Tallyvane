package tallyvane.platform.idempotency

import tallyvane.platform.kernel.Fallback
import kotlin.uuid.Uuid

/**
 * The value a client puts in `Idempotency-Key`: a UUID it generated once for one intention and sends
 * again, unchanged, every time it repeats that intention.
 *
 * A UUID and not any string, although the header would allow either. A string needs a length limit
 * and a character set to be safe to store and to log; a UUID is both already, and a client that has
 * to invent a key has no better source of one.
 *
 * [parse] answers `null` for anything else instead of throwing: a malformed header is the caller's
 * mistake, which the edge reports as a 400, not a failure of ours.
 */
@JvmInline
public value class IdempotencyKey private constructor(private val uuid: Uuid) {
    /**
     * Said to the claim that holds it, and to nobody else: storage reads it through
     * [Claim.writeTo].
     */
    internal fun uuid(): Uuid = uuid

    public companion object {
        /**
         * @return the key [text] spells, or `null` for a text that is not a UUID.
         */
        public fun parse(text: String): IdempotencyKey? = Fallback<IdempotencyKey?> { IdempotencyKey(Uuid.parse(text)) }
            .orElse(null)
    }
}
