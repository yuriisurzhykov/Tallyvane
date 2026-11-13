package tallyvane.catalog.infrastructure

import kotlinx.serialization.Serializable

/**
 * The shape of `catalog/catalog.json`.
 */
@Serializable
internal class CatalogFile(val skills: List<Skill>, val roles: List<Role>) {
    @Serializable
    class Skill(val slug: String, val name: String)

    @Serializable
    class Role(val slug: String, val name: String, val stations: List<Stop>)

    @Serializable
    class Stop(val skill: String, val importance: Double)
}
