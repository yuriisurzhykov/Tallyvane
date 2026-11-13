package tallyvane.catalog.infrastructure

import kotlinx.serialization.json.Json
import tallyvane.catalog.application.Catalog
import tallyvane.catalog.contract.RoleTrack
import tallyvane.catalog.contract.SkillCatalog
import tallyvane.catalog.contract.SkillName
import tallyvane.catalog.contract.Station

/**
 * Hands out the catalog that ships with the application, read from `catalog/catalog.json` on the classpath.
 *
 * A file and not a table: the list is curated, changes with a release and is the same for everyone, so there
 * is nothing to migrate and nothing to cache.
 */
public class CatalogFactory {
    /**
     * @throws IllegalArgumentException the file does not hold together; see [Catalog].
     */
    public fun catalog(): SkillCatalog {
        val text = checkNotNull(javaClass.getResource(FILE)) { "$FILE is not on the classpath." }.readText()
        val file = Json.decodeFromString<CatalogFile>(text)
        return Catalog(
            file.skills.map { SkillName(it.slug, it.name) },
            file.roles.map { role ->
                RoleTrack(role.slug, role.name, role.stations.map { Station(it.skill, it.importance) })
            },
        )
    }

    override fun toString(): String = "CatalogFactory"

    private companion object {
        const val FILE = "/catalog/catalog.json"
    }
}
