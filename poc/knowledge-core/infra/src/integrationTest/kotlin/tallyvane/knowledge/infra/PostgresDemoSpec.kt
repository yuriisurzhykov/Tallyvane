package tallyvane.knowledge.infra

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.application.AnswerNotUsable
import tallyvane.knowledge.application.KnowledgeCore
import tallyvane.knowledge.application.LlmStepFailed
import tallyvane.knowledge.application.Settings
import tallyvane.knowledge.domain.port.EmbeddingProvider
import tallyvane.knowledge.fixtures.ConceptEmbeddings
import tallyvane.knowledge.fixtures.DemoScenario
import tallyvane.knowledge.fixtures.DemoScript
import tallyvane.knowledge.fixtures.DemoWorld
import tallyvane.knowledge.fixtures.FixedPrompts
import tallyvane.knowledge.fixtures.ReadSkillForSeeds
import tallyvane.knowledge.fixtures.RecordingDiagnostics
import tallyvane.knowledge.fixtures.ScriptedLlm
import tallyvane.knowledge.infra.postgres.Postgres
import tallyvane.knowledge.infra.postgres.SeedSkill
import tallyvane.knowledge.infra.postgres.SeedTechnology

/** The world of the demo, over a real Postgres with pgvector and the scripted model: the same seeds, embeddings and wiring as the fakes. */
private class World(llm: ScriptedLlm) : AutoCloseable {
    val database = TestDatabase.fresh()
    val embeddings: EmbeddingProvider = ConceptEmbeddings(DemoWorld.CONCEPTS, DemoWorld.DIMENSIONS)
    val postgres = Postgres.open(database.url, database.user, database.password, DemoWorld.DIMENSIONS)
    val diagnostics = RecordingDiagnostics()
    val core: KnowledgeCore
    val store = postgres.knowledge()

    init {
        val dictionary =
            runBlocking {
                Seeding(
                    postgres,
                    embeddings,
                    DemoWorld.DIMENSIONS,
                    DemoWorld.SKILLS.map { skill -> ReadSkillForSeeds.of(skill).let { SeedSkill(it.slug.value, it.name, it.aliases) } },
                    DemoWorld.TECHNOLOGIES.map { SeedTechnology(it.name, it.aliases) },
                ).run()
            }
        core =
            KnowledgeCore(
                graph = store,
                cursors = store,
                log = store,
                skills = store,
                vacancies = store,
                llm = llm,
                embeddings = embeddings,
                prompts = FixedPrompts(),
                dictionary = dictionary,
                diagnostics = diagnostics,
                settings = Settings(),
                today = { DemoWorld.TODAY },
            )
    }

    override fun close() {
        postgres.close()
        database.drop()
    }
}

class PostgresDemoSpec :
    StringSpec(
        {
            "the demo runs over Postgres as it runs over the fakes" {
                val llm = DemoScript.llm()
                World(llm).use { world ->
                    runBlocking { DemoScenario.run(world.core, world.store, llm) { world.database.count("raw_entry") } }
                }
            }

            "the graph the demo leaves is in the tables the specification names" {
                val llm = DemoScript.llm()
                World(llm).use { world ->
                    runBlocking { DemoScenario.run(world.core, world.store, llm) { world.database.count("raw_entry") } }
                    val db = world.database
                    db.query("select name from company") shouldBe listOf("Acme")
                    db.query("select title from role") shouldBe listOf("Backend Engineer")
                    db.query("select is_current::text from role") shouldBe listOf("true")
                    db.query("select title from project") shouldBe listOf("Catalog cache")
                    db.query("select closed::text from project") shouldBe listOf("true")
                    db.query("select kind from action order by seq") shouldBe
                        listOf("investigated", "designed", "communicated", "built", "decided")
                    db.query("select name from technology where not seeded") shouldBe emptyList()
                    db.query("select distinct t.name from action_technology a join technology t on t.id = a.technology_id") shouldBe listOf("Redis")
                    db.query("select role_name from collaborator") shouldBe listOf("dba")
                    db.query("select value::text || unit from outcome where has_metric") shouldBe listOf("40%")
                    db.query("select dir || approx::text from outcome where has_metric") shouldBe listOf("downtrue")
                    db.count("action_outcome") shouldBe 2
                    db.query("select jsonb_array_length(canonical_bullets)::text from project") shouldBe listOf("1")
                    db.query("select stage from cursor_state") shouldBe listOf("AFTER_PROJECT")
                    db.query("select gap_code from question_log order by seq") shouldBe
                        listOf("OPENING", "ROLE_DATES", "PROJECT_TITLE", "NO_DECISION", "MORE_IN_PROJECT", "MORE_IN_ROLE")
                }
            }

            "an answer whose reading fails twice is kept, and the graph is left as it was" {
                val llm = ScriptedLlm.of("extract" to listOf("not json at all", "still not json"))
                World(llm).use { world ->
                    runBlocking {
                        val failure = shouldThrow<LlmStepFailed> { world.core.ingestAnswer.ingest(DemoScript.FIRST_ANSWER, null) }
                        failure.step shouldBe "extract"
                    }
                    world.database.count("raw_entry") shouldBe 1
                    world.database.count("company") shouldBe 0
                    world.database.count("action") shouldBe 0
                }
            }

            "an answer that names no company starts no job" {
                val llm = ScriptedLlm.of("extract" to listOf("""{"company":{"n":"Acme","q":"в компании Acme"}}"""))
                World(llm).use { world ->
                    runBlocking { shouldThrow<AnswerNotUsable> { world.core.ingestAnswer.ingest("Работаю бэкенд-инженером", null) } }
                    world.database.count("company") shouldBe 0
                    world.database.count("raw_entry") shouldBe 1
                }
            }

            "a reset removes what the person said and keeps the seeds" {
                val llm = DemoScript.llm()
                World(llm).use { world ->
                    runBlocking {
                        world.core.ingestAnswer.ingest(DemoScript.FIRST_ANSWER, null)
                        world.core.resetData.reset()
                        world.core.showGraph.show().nodes shouldBe emptyList()
                        world.core.nextQuestion.current().gap shouldBe "OPENING"
                    }
                    val db = world.database
                    listOf("raw_entry", "company", "role", "project", "action", "outcome", "evidence", "collaborator", "cursor_state").forEach {
                        db.count(it) shouldBe if (it == "cursor_state") 0 else 0
                    }
                    db.count("skill") shouldBe DemoWorld.SKILLS.size
                    db.query("select name from technology") shouldContainExactlyInAnyOrder DemoWorld.TECHNOLOGIES.map { it.name }
                    db.query("select count(*)::text from skill where embedding is null") shouldBe listOf("0")
                }
            }

            "a database made for other embeddings refuses to start" {
                val database = TestDatabase.fresh()
                try {
                    Postgres.open(database.url, database.user, database.password, DemoWorld.DIMENSIONS).close()
                    val failure = shouldThrow<StartupFailure> {
                        Postgres.open(database.url, database.user, database.password, DemoWorld.DIMENSIONS * 2)
                    }
                    failure.message shouldContain "EMBED_DIM"
                } finally {
                    database.drop()
                }
            }

            "a database that cannot be reached stops the start and names where it looked" {
                val failure = shouldThrow<StartupFailure> { Postgres.open("jdbc:postgresql://localhost:1/knowledge", "knowledge", "x", DemoWorld.DIMENSIONS) }
                failure.message shouldContain "localhost:1/knowledge"
            }
        },
    )
