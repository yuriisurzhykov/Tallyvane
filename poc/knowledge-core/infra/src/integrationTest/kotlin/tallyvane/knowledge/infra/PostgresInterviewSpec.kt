package tallyvane.knowledge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.application.KnowledgeCore
import tallyvane.knowledge.application.Settings
import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.fixtures.ConceptEmbeddings
import tallyvane.knowledge.fixtures.DemoWorld
import tallyvane.knowledge.fixtures.FixedPrompts
import tallyvane.knowledge.fixtures.ReadSkillForSeeds
import tallyvane.knowledge.fixtures.RecordingDiagnostics
import tallyvane.knowledge.fixtures.ScriptedLlm
import tallyvane.knowledge.infra.postgres.Postgres
import tallyvane.knowledge.infra.postgres.SeedSkill
import tallyvane.knowledge.infra.postgres.SeedTechnology
import kotlin.uuid.Uuid

private const val FIRST = "Работаю в Acme бэкенд-инженером с 2022. Внедрил кеш на Redis."

private const val FIRST_REPLY =
    """{"company":{"n":"Acme","q":"в Acme"},
        "role":{"title":"Backend Engineer","start":"2022","current":true,"q":"бэкенд-инженером с 2022"},
        "project":{"title":"Catalog cache","problem":"Slow reads","q":"Внедрил кеш на Redis"},
        "actions":[{"kind":"built","verb":"implemented","what":"a catalog cache","techs":[{"n":"Redis","q":"Redis"}],"q":"Внедрил кеш на Redis"}]}"""

/** A Postgres-backed core that can be closed and opened again over the same database, as a restart does. */
private class Deployment(private val database: TestDatabase, private val llm: ScriptedLlm) : AutoCloseable {
    private val embeddings = ConceptEmbeddings(DemoWorld.CONCEPTS, DemoWorld.DIMENSIONS)
    private var postgres: Postgres? = null

    lateinit var core: KnowledgeCore
    lateinit var store: tallyvane.knowledge.infra.postgres.PostgresKnowledge

    init {
        start()
    }

    fun start() {
        val opened = Postgres.open(database.url, database.user, database.password, DemoWorld.DIMENSIONS)
        postgres = opened
        store = opened.knowledge()
        val dictionary =
            runBlocking {
                Seeding(
                    opened,
                    embeddings,
                    DemoWorld.DIMENSIONS,
                    DemoWorld.SKILLS.map { skill -> ReadSkillForSeeds.of(skill).let { SeedSkill(it.slug.value, it.name, it.aliases) } },
                    DemoWorld.TECHNOLOGIES.map { SeedTechnology(it.name, it.aliases) },
                ).run()
            }
        core =
            KnowledgeCore(
                store, store, store, store, store, llm, embeddings, FixedPrompts(), dictionary, RecordingDiagnostics(), Settings(),
                { DemoWorld.TODAY },
            )
    }

    fun stop() {
        postgres?.close()
        postgres = null
    }

    override fun close() {
        stop()
        database.drop()
    }
}

class PostgresInterviewSpec :
    StringSpec(
        {
            "an action close in meaning to an old one is offered as a merge, and accepting folds it into the old one" {
                val again =
                    """{"actions":[{"kind":"built","verb":"implemented","what":"a catalog cache","techs":[{"n":"Redis","q":"Redis"}],"q":"снова внедрил кеш на Redis"}]}"""
                val database = TestDatabase.fresh()
                Deployment(database, ScriptedLlm.of("extract" to listOf(FIRST_REPLY, again))).use { deployment ->
                    runBlocking {
                        deployment.core.ingestAnswer.ingest(FIRST, null)
                        val candidate = deployment.core.ingestAnswer.ingest("Ещё раз: снова внедрил кеш на Redis", null).mergeCandidate!!
                        candidate.newLabel shouldBe "implemented a catalog cache"
                        database.count("action") shouldBe 2

                        deployment.core.resolveMerge.resolve(
                            ActionId(Uuid.parse(candidate.newActionId)),
                            ActionId(Uuid.parse(candidate.existingActionId)),
                            accept = true,
                        )
                        database.count("action") shouldBe 1
                        deployment.store.quotesOfNode(candidate.existingActionId) shouldHaveSize 2
                        database.count("action_technology") shouldBe 1
                        database.count("action_skill") shouldBe database.query("select count(distinct skill_id) from action_skill").single().toInt()
                    }
                }
            }

            "declining the merge leaves both actions" {
                val again =
                    """{"actions":[{"kind":"built","verb":"implemented","what":"a catalog cache","q":"снова внедрил кеш на Redis"}]}"""
                val database = TestDatabase.fresh()
                Deployment(database, ScriptedLlm.of("extract" to listOf(FIRST_REPLY, again))).use { deployment ->
                    runBlocking {
                        deployment.core.ingestAnswer.ingest(FIRST, null)
                        val candidate = deployment.core.ingestAnswer.ingest("Ещё раз: снова внедрил кеш на Redis", null).mergeCandidate!!
                        deployment.core.resolveMerge.resolve(
                            ActionId(Uuid.parse(candidate.newActionId)),
                            ActionId(Uuid.parse(candidate.existingActionId)),
                            accept = false,
                        )
                        database.count("action") shouldBe 2
                    }
                }
            }

            "a second project in the role and a job before it are kept as a project and a company of their own" {
                val second =
                    """{"project":{"title":"Importer","problem":"Manual imports","q":"писал импортёр"},
                        "actions":[{"kind":"built","verb":"wrote","what":"a cache warmer","q":"писал импортёр"}]}"""
                val before =
                    """{"company":{"n":"Initech","q":"в Initech"},
                        "role":{"title":"Developer","start":"2018","end":"2021","q":"в Initech разработчиком"},
                        "actions":[{"kind":"built","verb":"built","what":"cached reports","q":"делал отчёты"}]}"""
                val bullets =
                    listOf(
                        """{"bullets":[{"text":"Implemented a catalog cache using Redis.","a":["A1"]}]}""",
                        """{"bullets":[{"text":"Wrote a cache warmer.","a":["A1"]}]}""",
                    )
                val database = TestDatabase.fresh()
                Deployment(database, ScriptedLlm.of("extract" to listOf(FIRST_REPLY, second, before), "bullets" to bullets)).use { deployment ->
                    runBlocking {
                        val core = deployment.core
                        core.ingestAnswer.ingest(FIRST, null)
                        repeat(2) { core.ingestAnswer.ingest("нет", null) }
                        core.ingestAnswer.ingest("no metric", null)
                        core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "MORE_IN_ROLE"
                        core.ingestAnswer.ingest("Да, писал импортёр", null).nextQuestion.gap shouldBe "NO_DECISION"
                        repeat(2) { core.ingestAnswer.ingest("нет", null) }
                        core.ingestAnswer.ingest("no metric", null)
                        core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "MORE_IN_ROLE"
                        core.ingestAnswer.ingest("нет", null).nextQuestion.text shouldBe "What did you do before Acme?"
                        core.ingestAnswer.ingest("Раньше в Initech разработчиком делал отчёты", null).nextQuestion.gap shouldBe "PROJECT_TITLE"

                    }
                    database.query("select coalesce(title, '-') from project order by seq") shouldContainExactly
                        listOf("Catalog cache", "Importer", "-")
                    database.query("select name from company order by name") shouldContainExactly listOf("Acme", "Initech")
                    database.count("role") shouldBe 2
                    database.count("project") shouldBe 3
                    database.query("select count(*)::text from project where closed") shouldBe listOf("2")
                }
            }

            "the interview picks up where it was after a restart" {
                val database = TestDatabase.fresh()
                Deployment(database, ScriptedLlm.of("extract" to listOf(FIRST_REPLY))).use { deployment ->
                    runBlocking {
                        val asked = deployment.core.ingestAnswer.ingest(FIRST, null).nextQuestion
                        asked.gap shouldBe "NO_DECISION"

                        deployment.stop()
                        deployment.start()

                        val again = deployment.core.nextQuestion.current()
                        again.gap shouldBe "NO_DECISION"
                        again.id shouldBe asked.id
                        deployment.core.ingestAnswer.ingest("нет", QuestionId(Uuid.parse(asked.id))).nextQuestion.gap shouldBe "NO_COLLAB"
                    }
                }
            }
        },
    )
