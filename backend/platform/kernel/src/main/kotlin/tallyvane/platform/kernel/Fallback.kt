package tallyvane.platform.kernel

import kotlin.coroutines.cancellation.CancellationException

/**
 * A chain of attempts that ends in a value, for recovery that never leaves the
 * function it happens in.
 *
 * Nested `try`/`catch` hides a linear intent inside an indented tree: the
 * deeper the fallback, the further right it is written. A chain states the same
 * thing in reading order — try this, otherwise that, otherwise take this value:
 *
 * ```
 * val zone = Fallback { ZoneId.of(preferred) }
 *     .or { ZoneId.of(legacy) }
 *     .orElse(ZoneOffset.ofHours(fixedOffset))
 * ```
 *
 * Each attempt runs only if every earlier one failed, and [orElse] always
 * supplies a value, so no failure escapes the chain. That is the whole scope of
 * this type. When a failure must reach the caller instead, the answer is a
 * named sealed outcome for that operation, not this class and not
 * [kotlin.Result].
 *
 * ### What it catches, and what it refuses to catch
 *
 * [CancellationException] is rethrown rather than counted as a failed attempt.
 * Swallowing it would let a cancelled coroutine carry on running, which is
 * exactly why `kotlin.runCatching` is the wrong tool here: it catches every
 * [Throwable], cancellation included. [Error] propagates for the same reason in
 * the other direction — a `StackOverflowError` is not a condition worth
 * offering an alternative for. Everything between, every [Exception], is a
 * failed attempt and moves the chain along.
 *
 * Attempts are inlined, so a chain may wrap suspending calls and reads the same
 * from suspending and ordinary code.
 *
 * Failed attempts are discarded rather than collected. That is deliberate for
 * the case this type serves, where each failure is an expected condition; a
 * failure worth logging means the fallback is hiding something, and that
 * belongs in an outcome the caller can inspect.
 *
 * Why not `kotlin.runCatching`, and why the constructor is
 * `@PublishedApi internal`: `backend/platform/kernel/README.md`.
 */
public class Fallback<T> @PublishedApi internal constructor(
    /**
     * Successful values so far. Empty means every attempt failed; a
     * single-element list, including of `null`, is a success. A list
     * rather than `T?` keeps those two states distinct.
     */
    @PublishedApi internal val resolved: List<T>,
) {
    /**
     * Runs [next] only if no earlier attempt in this chain produced a value.
     *
     * @return this chain unchanged when it already holds a value, otherwise
     * a chain holding the result of [next], or an empty one if it failed.
     */
    public inline fun or(next: () -> T): Fallback<T> {
        if (resolved.isNotEmpty()) {
            return this
        }
        return of(next)
    }

    /**
     * The value of the first successful attempt, or [default] if every
     * attempt failed. A successful `null` is a value, not a failure.
     */
    public fun orElse(default: T): T =
        if (resolved.isEmpty()) {
            default
        } else {
            resolved.first()
        }

    /**
     * Starts a [Fallback] chain. Callers write `Fallback { ... }`.
     */
    public companion object {
        /**
         * Starts a chain with [first], written `Fallback { ... }` at the
         * call site.
         */
        public inline operator fun <T> invoke(first: () -> T): Fallback<T> = of(first)

        /**
         * Runs [block] and encapsulates the outcome.
         *
         * Public only to the compiler: [or] and [invoke] are inline and
         * cannot reach an internal declaration otherwise.
         */
        @PublishedApi
        @Suppress("TooGenericExceptionCaught", "RethrowCaughtException", "SwallowedException")
        internal inline fun <T> of(block: () -> T): Fallback<T> =
            try {
                Fallback(listOf(block()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Fallback(emptyList())
            }
    }
}
