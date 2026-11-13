package tallyvane.catalog.contract

/**
 * One skill on the way to a role, and how much the role leans on it, above 0 and up to 1.
 */
public class Station(public val skill: String, public val importance: Double) {
    override fun equals(other: Any?): Boolean =
        other is Station && other.skill == skill && other.importance == importance

    override fun hashCode(): Int = listOf(skill, importance).hashCode()

    override fun toString(): String = "Station($skill, $importance)"
}
