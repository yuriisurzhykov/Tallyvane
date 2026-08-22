package tallyvane.platform.kernel

/**
 * An [IdGenerator] that yields `id-1`, `id-2`, … from a known sequence.
 *
 * Lives in `src/test` so it does not ship in the production jar (ADR-044).
 */
internal class IdGeneratorFake(
    private var sequence: Int = 0,
) : IdGenerator {
    override fun next(): String {
        sequence += 1
        return "id-$sequence"
    }
}
