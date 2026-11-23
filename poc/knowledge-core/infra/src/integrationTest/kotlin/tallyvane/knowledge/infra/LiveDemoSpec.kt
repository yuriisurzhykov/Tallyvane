package tallyvane.knowledge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.fixtures.DemoScript
import java.nio.file.Path
import kotlin.uuid.Uuid

private const val MAX_TURNS = 14

/** What the person says to each question the interview can ask after the first answer. */
private val REPLIES =
    mapOf(
        "ROLE_TITLE" to "Backend Engineer",
        "ROLE_DATES" to DemoScript.DATES_ANSWER,
        "PROJECT_TITLE" to DemoScript.TITLE_ANSWER,
        "PROJECT_PROBLEM" to "The database kept degrading under load",
        "NO_DECISION" to DemoScript.DECISION_ANSWER,
        "NO_COLLAB" to "нет",
        "NO_METRIC" to "no metric",
        "MORE_IN_PROJECT" to DemoScript.NOTHING_MORE,
        "MORE_IN_ROLE" to DemoScript.NOTHING_MORE,
        "PREVIOUS_ROLE" to DemoScript.NOTHING_MORE,
    )

/**
 * The demo of the specification against the real Anthropic API and the real embeddings, and so opt-in:
 * `LIVE_LLM=1 ./gradlew :infra:integrationTest --tests '*LiveDemoSpec*'`, with `ANTHROPIC_API_KEY`, `LLM_MODEL` and the
 * rest of `.env` in place, Ollama running with the embedding model, and a Postgres as for the other integration tests.
 *
 * A real model does not say the same thing twice, so this checks what must hold whatever it says: the graph holds
 * what was said, the interview ends, the vacancy is matched, and every résumé bullet has the person's words behind
 * it and no number the person did not say.
 */
class LiveDemoSpec :
    StringSpec(
        {
            "the demo runs against the real model and embeddings".config(enabled = System.getenv("LIVE_LLM") == "1") {
                val database = TestDatabase.fresh()
                try {
                    val env =
                        Environment.ofProcess(Path.of(".env"))
                            .with(mapOf("DATABASE_URL" to database.url, "DATABASE_USER" to database.user, "DATABASE_PASSWORD" to database.password))
                    val assembled = assemble(env)
                    val core = assembled.core
                    runBlocking {
                        val said = mutableListOf(DemoScript.FIRST_ANSWER)
                        core.nextQuestion.current().gap shouldBe "OPENING"

                        var next = core.ingestAnswer.ingest(DemoScript.FIRST_ANSWER, null).nextQuestion
                        val graph = core.showGraph.show()
                        graph.nodes.filter { it.type == "company" }.map { it.label.lowercase() } shouldContain "acme"
                        graph.nodes.filter { it.type == "technology" }.map { it.label.lowercase() } shouldContain "redis"
                        graph.nodes.count { it.type == "action" } shouldBeGreaterThanOrEqual 3
                        graph.nodes.any { it.type == "outcome" } shouldBe true

                        var turns = 0
                        while (next.gap != "DONE" && turns++ < MAX_TURNS) {
                            val reply = REPLIES[next.gap] ?: error("The interview asked something the test has no answer for: ${next.gap}")
                            said += reply
                            next = core.ingestAnswer.ingest(reply, QuestionId(Uuid.parse(next.id))).nextQuestion
                        }
                        next.gap shouldBe "DONE"

                        val vacancy = core.parseVacancy.parse(DemoScript.VACANCY_TEXT)
                        vacancy.reqs.map { it.skill }.shouldContain("redis")
                        val id = VacancyId(Uuid.parse(vacancy.vacancyId))
                        val match = core.matchAndScore.match(id)
                        match.gaps.single { it.skill == "redis" }.missing shouldBe false
                        match.gaps.firstOrNull { it.skill == "kafka" }?.missing?.shouldBe(true)

                        val resume = core.buildResume.build(id)
                        resume.bullets.shouldNotBeEmpty()
                        resume.markdown shouldContain "Acme"
                        val everythingSaid = said.joinToString(" ")
                        resume.bullets.forEach { bullet ->
                            bullet.evidence.shouldNotBeEmpty()
                            Regex("\\d+").findAll(bullet.text).map { it.value }.forEach { number -> everythingSaid shouldContain number }
                        }

                        assembled.calls.recent().shouldNotBeEmpty()
                    }
                } finally {
                    database.drop()
                }
            }
        },
    )
