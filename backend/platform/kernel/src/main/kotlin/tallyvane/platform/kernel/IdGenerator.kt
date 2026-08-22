package tallyvane.platform.kernel

/**
 * Identity as a collaborator, not a random generator called from domain code.
 *
 * Production will draw real identifiers; tests construct an `IdGeneratorFake`
 * in `src/test` with a known sequence so assertions can name the ids they
 * expect. The fake is not nested on this type.
 */
public interface IdGenerator {
    public fun next(): String
}
