package tallyvane.platform.kernel

/**
 * Identity as a collaborator, not a random generator called from domain code.
 *
 * Production will draw real identifiers; tests construct an `IdGeneratorFake`
 * in `src/test` with a known sequence so assertions can name the ids they
 * expect. Direct `UUID.randomUUID()` outside an implementation of this
 * interface is an architecture failure (`no-ambient-random`). The fake is not
 * nested on this type.
 *
 * Why a port rather than `UUID.randomUUID()`, and why the fake is not nested:
 * `backend/platform/kernel/README.md`.
 */
public interface IdGenerator {
    /**
     * The next identifier in this generator's sequence.
     */
    public fun next(): String
}
