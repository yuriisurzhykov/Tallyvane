package tallyvane.knowledge.infra.postgres

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.TechnologyDictionary

/** A skill or technology that comes with the product, as the seed JSON files list it. */
class SeedSkill(val slug: String, val name: String, val aliases: List<String>) {
    /** The text whose meaning stands for the skill. */
    fun embeddingText(): String = (listOf(name) + aliases).joinToString(" ")
}

class SeedTechnology(val name: String, val aliases: List<String>)

/** Writes the seeds to the tables they live in, and reads back what still needs embedding. */
internal class PostgresSeeds(private val db: Db) {
    /** Adds the skills that are missing and updates those whose name or aliases changed, which are then embedded afresh. */
    suspend fun skills(seeds: List<SeedSkill>) {
        db.tx {
            seeds.forEach { seed ->
                val existing = SkillsTable.selectAll().where { SkillsTable.slug eq seed.slug }.singleOrNull()
                if (existing == null) {
                    SkillsTable.insert {
                        it[id] = newId()
                        it[slug] = seed.slug
                        it[name] = seed.name
                        it[aliases] = seed.aliases
                    }
                } else if (existing[SkillsTable.name] != seed.name || existing[SkillsTable.aliases] != seed.aliases) {
                    SkillsTable.update({ SkillsTable.slug eq seed.slug }) {
                        it[name] = seed.name
                        it[aliases] = seed.aliases
                        it[embedding] = null
                    }
                }
            }
        }
    }

    suspend fun technologies(seeds: List<SeedTechnology>) {
        db.tx {
            seeds.forEach { seed ->
                val existing = TechnologiesTable.selectAll().where { TechnologiesTable.name eq seed.name }.singleOrNull()
                if (existing == null) {
                    TechnologiesTable.insert {
                        it[id] = newId()
                        it[name] = seed.name
                        it[aliases] = seed.aliases
                        it[seeded] = true
                    }
                } else {
                    TechnologiesTable.update({ TechnologiesTable.name eq seed.name }) {
                        it[aliases] = seed.aliases
                        it[seeded] = true
                    }
                }
            }
        }
    }

    /** The skills that have no embedding yet, as the slug and the text to embed. */
    suspend fun skillsWithoutEmbedding(): List<Pair<String, String>> =
        db.tx {
            SkillsTable.selectAll().where { SkillsTable.embedding.isNull() }.map {
                it[SkillsTable.slug] to SeedSkill(it[SkillsTable.slug], it[SkillsTable.name], it[SkillsTable.aliases]).embeddingText()
            }
        }

    suspend fun embedSkill(slug: String, embedding: Embedding) {
        db.tx { SkillsTable.update({ SkillsTable.slug eq slug }) { it[SkillsTable.embedding] = valuesOf(embedding) } }
    }

    /** Every technology the product knows, under its canonical name and its aliases. */
    suspend fun dictionary(): TechnologyDictionary =
        db.tx {
            TechnologyDictionary(
                TechnologiesTable.selectAll().map { TechnologyDictionary.Entry(it[TechnologiesTable.name], it[TechnologiesTable.aliases]) },
            )
        }
}
