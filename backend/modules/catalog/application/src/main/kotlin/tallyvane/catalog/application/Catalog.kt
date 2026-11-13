package tallyvane.catalog.application

import tallyvane.catalog.contract.RoleTrack
import tallyvane.catalog.contract.SkillCatalog
import tallyvane.catalog.contract.SkillName

/**
 * The catalog, checked once when it is built: a catalog that names a skill twice, a role with no stations or a
 * station whose skill is unknown does not start the application.
 */
public class Catalog(private val skills: List<SkillName>, private val roles: List<RoleTrack>) : SkillCatalog {
    init {
        val slugs = skills.map { it.slug }
        require(slugs.all(SLUG::matches)) { "A slug is lowercase words joined by dashes." }
        require(slugs.distinct().size == slugs.size) { "A skill is listed once." }
        require(roles.map { it.slug }.distinct().size == roles.size) { "A role is listed once." }
        roles.forEach { role ->
            require(role.stations.isNotEmpty()) { "The role ${role.slug} has no stations." }
            role.stations.forEach { station ->
                require(station.skill in slugs) { "The role ${role.slug} names the unknown skill ${station.skill}." }
                require(station.importance > 0 && station.importance <= 1) {
                    "An importance is above 0 and at most 1, but ${role.slug} has ${station.importance}."
                }
            }
        }
    }

    override fun skills(): List<SkillName> = skills

    override fun roles(): List<RoleTrack> = roles

    override fun role(slug: String): RoleTrack? = roles.firstOrNull { it.slug == slug }

    override fun toString(): String = "Catalog(skills=${skills.size}, roles=${roles.size})"

    private companion object {
        val SLUG = Regex("""[a-z][a-z0-9]*(-[a-z0-9]+)*""")
    }
}
