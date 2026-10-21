package tallyvane.knowledge.fixtures

import tallyvane.knowledge.application.KnowledgeCore
import tallyvane.knowledge.application.Settings
import tallyvane.knowledge.domain.Skill
import tallyvane.knowledge.domain.SkillSlug
import tallyvane.knowledge.domain.TechnologyDictionary
import java.time.LocalDate

/** The seed data, the fake embeddings and the wiring of the demo, so tests and the infrastructure build the same world. */
class DemoWorld(llm: ScriptedLlm, settings: Settings = Settings()) {
    val diagnostics = RecordingDiagnostics()
    val embeddings = ConceptEmbeddings(CONCEPTS, DIMENSIONS)
    val store =
        InMemoryKnowledge(
            SKILLS,
            SKILLS.associate { skill ->
                val read = ReadSkill.of(skill)
                read.slug to embeddings.vectorOf(read.name + " " + read.aliases.joinToString(" "))
            },
        )
    val core =
        KnowledgeCore(
            graph = store,
            cursors = store,
            log = store,
            skills = store,
            vacancies = store,
            llm = llm,
            embeddings = embeddings,
            prompts = FixedPrompts(),
            dictionary = DICTIONARY,
            diagnostics = diagnostics,
            settings = settings,
            today = { TODAY },
        )

    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 21)
        const val DIMENSIONS = 8

        private fun skill(slug: String, name: String, vararg aliases: String) = Skill(SkillSlug(slug), name, aliases.toList())

        val SKILLS =
            listOf(
                skill("caching", "Caching", "cache"),
                skill("redis", "Redis", "редис"),
                skill("performance-profiling", "Performance profiling"),
                skill("cross-team-collaboration", "Cross-team collaboration", "team"),
                skill("postgresql", "PostgreSQL", "postgres"),
                skill("kafka", "Kafka"),
                skill("ci-cd", "CI/CD", "continuous integration pipeline"),
                skill("testing", "Testing", "unit tests"),
            )

        val CONCEPTS: Map<String, Map<Int, Float>> =
            mapOf(
                "cach" to mapOf(0 to 1f),
                "redis" to mapOf(1 to 1f),
                "profil" to mapOf(2 to 1f),
                "database" to mapOf(4 to 0.45f),
                "team" to mapOf(3 to 1f),
                "dba" to mapOf(3 to 1f),
                "collaborat" to mapOf(3 to 1f),
                "postgres" to mapOf(4 to 1f),
                "kafka" to mapOf(5 to 1f),
                "chose" to mapOf(6 to 1f),
                "memcached" to mapOf(6 to 1f),
            )

        val TECHNOLOGIES =
            listOf(
                TechnologyDictionary.Entry("Redis", listOf("редис")),
                TechnologyDictionary.Entry("PostgreSQL", listOf("postgres", "постгрес")),
                TechnologyDictionary.Entry("Kafka", listOf("кафка")),
                TechnologyDictionary.Entry("Memcached", listOf("мемкеш")),
            )

        val DICTIONARY = TechnologyDictionary(TECHNOLOGIES)
    }
}
