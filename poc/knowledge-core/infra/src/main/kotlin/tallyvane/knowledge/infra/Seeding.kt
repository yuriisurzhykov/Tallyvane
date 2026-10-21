package tallyvane.knowledge.infra

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tallyvane.knowledge.domain.TechnologyDictionary
import tallyvane.knowledge.domain.port.EmbeddingProvider
import tallyvane.knowledge.infra.postgres.Postgres
import tallyvane.knowledge.infra.postgres.SeedSkill
import tallyvane.knowledge.infra.postgres.SeedTechnology
import tallyvane.knowledge.infra.postgres.valuesOf

/**
 * What the product knows before anyone has said anything: the skills, which are embedded here if they have not been,
 * and the technologies with their other spellings. It also asks the embedding model for one vector, because a model
 * that does not give [dimensions] numbers would make every later answer fail, and this is the cheapest place to say so.
 */
class Seeding(
    private val postgres: Postgres,
    private val embeddings: EmbeddingProvider,
    private val dimensions: Int,
    private val skills: List<SeedSkill>,
    private val technologies: List<SeedTechnology>,
) {
    suspend fun run(): TechnologyDictionary {
        verifyDimensions()
        val seeds = postgres.seeds()
        seeds.skills(skills)
        seeds.technologies(technologies)
        seeds.skillsWithoutEmbedding().chunked(BATCH).forEach { batch ->
            val vectors = embeddings.embed(batch.map { it.second })
            batch.zip(vectors).forEach { (skill, vector) -> seeds.embedSkill(skill.first, vector) }
        }
        return seeds.dictionary()
    }

    private suspend fun verifyDimensions() {
        val actual = valuesOf(embeddings.embed(listOf("dimension check")).single()).size
        if (actual != dimensions) {
            throw StartupFailure(
                "The embedding model gives vectors of $actual dimensions and EMBED_DIM is $dimensions. " +
                    "Set EMBED_DIM to $actual, with a fresh database if one was already made.",
            )
        }
    }

    @Serializable
    private class SkillJson(val slug: String, val name: String, val aliases: List<String> = emptyList())

    @Serializable
    private class TechnologyJson(val name: String, val aliases: List<String> = emptyList())

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val BATCH = 32

        /** The seeding the product ships with: `seed/skills.json` and `seed/technologies.json` inside the application. */
        fun bundled(postgres: Postgres, embeddings: EmbeddingProvider, dimensions: Int): Seeding =
            Seeding(
                postgres,
                embeddings,
                dimensions,
                json.decodeFromString<List<SkillJson>>(resource("seed/skills.json")).map { SeedSkill(it.slug, it.name, it.aliases) },
                json.decodeFromString<List<TechnologyJson>>(resource("seed/technologies.json")).map { SeedTechnology(it.name, it.aliases) },
            )

        private fun resource(path: String): String =
            (Seeding::class.java.getResourceAsStream("/$path") ?: throw StartupFailure("The seed file $path is missing."))
                .use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
