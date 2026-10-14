package tallyvane.authentication.application

/**
 * What putting a version in force would do to the people it applies to (ADR-098).
 *
 * @param people How many people the purpose applies to: every account, or every administrator for `admin_login`.
 * @param cannotPass How many of them could not get to the end of a sign-in under it. Never more than [people].
 */
public class Consequences(private val people: Int, private val cannotPass: Int) {
    init {
        require(people >= 0 && cannotPass in 0..people) {
            "$cannotPass people cannot pass out of $people: the count of those who cannot is part of the people."
        }
    }

    /**
     * Tells [record] how many people it reaches and how many of them it would shut out.
     */
    public fun writeTo(record: Record) {
        record.consequences(people, cannotPass)
    }

    /**
     * Whether there are people, and every one of them would be shut out.
     */
    public fun shutsOutEveryone(): Boolean = people > 0 && cannotPass == people

    override fun equals(other: Any?): Boolean =
        other is Consequences && other.people == people && other.cannotPass == cannotPass

    override fun hashCode(): Int = 31 * people + cannotPass

    override fun toString(): String = "Consequences(people=$people, cannotPass=$cannotPass)"

    /**
     * What the consequences tell whoever shows them.
     */
    public fun interface Record {
        public fun consequences(people: Int, cannotPass: Int)
    }
}
