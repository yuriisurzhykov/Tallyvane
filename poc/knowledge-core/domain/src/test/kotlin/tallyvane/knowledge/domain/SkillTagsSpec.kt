package tallyvane.knowledge.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private val catalog =
    listOf(
        Skill(slug("redis"), "Redis", listOf("редис")),
        Skill(slug("caching"), "Caching", listOf("cache")),
        Skill(slug("postgresql"), "PostgreSQL", listOf("postgres")),
    )

private fun weights(tags: List<Tag>): Map<String, Double> {
    val found = linkedMapOf<String, Double>()
    tags.forEach { tag ->
        tag.writeTo(
            object : Tag.Record {
                override fun tag(skill: SkillSlug, weight: Double) {
                    found[skill.value] = weight
                }
            },
        )
    }
    return found
}

class SkillTagsSpec :
    StringSpec(
        {
            val tags = SkillTags(threshold = 0.55, limit = 3)

            "the nearest skills that are near enough are taken, weighted by how near" {
                val nearest = listOf(SkillMatch(slug("caching"), 0.80), SkillMatch(slug("postgresql"), 0.40), SkillMatch(slug("redis"), 0.60))
                weights(tags.pick(nearest, catalog, emptyList())) shouldBe mapOf("caching" to 0.80, "redis" to 0.60)
            }

            "a skill exactly at the threshold is taken" {
                weights(tags.pick(listOf(SkillMatch(slug("caching"), 0.55)), catalog, emptyList())) shouldBe mapOf("caching" to 0.55)
            }

            "no more than the limit are taken, the nearest first" {
                val many = listOf("a", "b", "c", "d", "e").mapIndexed { i, s -> SkillMatch(slug(s), 0.9 - i * 0.05) }
                weights(SkillTags(0.5, 3).pick(many, emptyList(), emptyList())).keys shouldBe setOf("a", "b", "c")
            }

            "a technology that is a skill is tagged at full weight, whatever its distance" {
                weights(tags.pick(emptyList(), catalog, listOf(Technology("Redis")))) shouldBe mapOf("redis" to 1.0)
                weights(tags.pick(emptyList(), catalog, listOf(Technology("редис")))) shouldBe mapOf("redis" to 1.0)
            }

            "a skill found both ways keeps the higher weight" {
                val nearest = listOf(SkillMatch(slug("redis"), 0.70), SkillMatch(slug("caching"), 0.80))
                weights(tags.pick(nearest, catalog, listOf(Technology("Redis")))) shouldBe mapOf("redis" to 1.0, "caching" to 0.80)
            }

            "a technology that is not a skill adds no tag" {
                tags.pick(emptyList(), catalog, listOf(Technology("Memcached"))).size shouldBe 0
            }

            "actions are alike from the threshold up" {
                val rule = DuplicateRule(0.85)
                rule.suspects(0.85) shouldBe true
                rule.suspects(0.8499) shouldBe false
                rule.suspects(0.99) shouldBe true
            }

            "similarity is the cosine of the angle between two embeddings" {
                val a = Embedding(floatArrayOf(1f, 0f))
                a.similarityTo(Embedding(floatArrayOf(2f, 0f))) shouldBe 1.0
                a.similarityTo(Embedding(floatArrayOf(0f, 3f))) shouldBe 0.0
                a.similarityTo(Embedding(floatArrayOf(-1f, 0f))) shouldBe -1.0
                a.similarityTo(Embedding(floatArrayOf(0f, 0f))) shouldBe 0.0
            }
        },
    )
