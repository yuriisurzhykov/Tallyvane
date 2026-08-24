package tallyvane.platform.kernel

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identity as a collaborator, not a random generator called from domain code.
 *
 * Production draws UUIDv7, whose millisecond prefix makes ids monotonic —
 * ARCHITECTURE.md §8.1 relies on that for index clustering and natural
 * ordering. Tests construct an `IdGeneratorFake` in `src/test` with a known
 * sequence so assertions can name the ids they expect. Direct
 * `UUID.randomUUID()` outside an implementation of this interface is an
 * architecture failure (`no-ambient-random`). The fake is not nested on this
 * type.
 *
 * An id from here is not a secret. UUIDv7 spends at most 74 bits on randomness
 * and publishes the moment it was minted, so a session token or any other
 * value that must be unguessable needs its own port, not this one.
 *
 * Why a port rather than `UUID.randomUUID()`, and why the fake is not nested:
 * `backend/platform/kernel/README.md`.
 */
public interface IdGenerator {
    /**
     * The next identifier in this generator's sequence.
     */
    public fun next(): Uuid

    /**
     * The generator production runs on: UUIDv7 from the standard library.
     *
     * `Uuid.generateV7` is monotonic, which is the property §8.1 asks for and
     * the reason `generateV7NonMonotonicAt` is not used here — passing an
     * injected instant would read better but would let two ids minted in the
     * same millisecond sort in either order. It nests on the port because it
     * reaches no technology, only the platform's clock and CSPRNG.
     */
    public class Uuid7 : IdGenerator {
        @OptIn(ExperimentalUuidApi::class)
        override fun next(): Uuid = Uuid.generateV7()
    }
}
