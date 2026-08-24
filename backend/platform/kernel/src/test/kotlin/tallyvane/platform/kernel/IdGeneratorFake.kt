package tallyvane.platform.kernel

import kotlin.uuid.Uuid

/**
 * An [IdGenerator] that yields `…001`, `…002`, … from a known sequence.
 *
 * The values are shaped like UUIDv7 — version nibble `7`, IETF variant — so a
 * caller that parses, stores or sorts them behaves as it does in production,
 * while a failed assertion still names an id a reader can recognise.
 *
 * Lives in `src/test` so it does not ship in the production jar (ADR-044).
 */
internal class IdGeneratorFake(private var sequence: Int = 0) : IdGenerator {
    override fun next(): Uuid {
        sequence += 1
        return Uuid.parse("00000000-0000-7000-8000-%012d".format(sequence))
    }
}
