package tallyvane.catalog.contract

/**
 * A role a person can aim for, and the skills on the way to it, in the order the route shows them.
 */
public class RoleTrack(public val slug: String, public val name: String, public val stations: List<Station>) {
    override fun equals(other: Any?): Boolean =
        other is RoleTrack && other.slug == slug && other.name == name && other.stations == stations

    override fun hashCode(): Int = listOf(slug, name, stations).hashCode()

    override fun toString(): String = "RoleTrack($slug)"
}
