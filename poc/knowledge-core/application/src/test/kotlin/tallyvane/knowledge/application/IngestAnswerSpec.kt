package tallyvane.knowledge.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.fixtures.DemoWorld
import tallyvane.knowledge.fixtures.ScriptedLlm
import kotlin.uuid.Uuid

private const val FIRST = "Работаю в Acme бэкенд-инженером с 2022. Внедрил кеш на Redis."

private const val FIRST_REPLY =
    """{"company":{"n":"Acme","q":"в Acme"},
        "role":{"title":"Backend Engineer","start":"2022","current":true,"q":"бэкенд-инженером с 2022"},
        "project":{"title":"Catalog cache","problem":"Slow reads","q":"Внедрил кеш на Redis"},
        "actions":[{"kind":"built","verb":"implemented","what":"a catalog cache","techs":[{"n":"Redis","q":"Redis"}],"q":"Внедрил кеш на Redis"}]}"""

private fun world(vararg script: Pair<String, List<String>>) = DemoWorld(ScriptedLlm.of(*script))

class IngestAnswerSpec :
    StringSpec(
        {
            "a reply that is not JSON is sent back once with the reason, and a good second reply is used" {
                runBlocking {
                    val llm = ScriptedLlm.of("extract" to listOf("sorry, I cannot do that", FIRST_REPLY))
                    val core = DemoWorld(llm).core
                    core.ingestAnswer.ingest(FIRST, null).nodes.map { it.type } shouldContain "action"
                    llm.callsTo("extract") shouldBe 2
                    llm.promptsOf("extract")[1].dynamicSuffix shouldContain "Your previous reply was rejected"
                    llm.promptsOf("extract")[1].dynamicSuffix shouldContain "no JSON object"
                    llm.promptsOf("extract")[0].cachedPrefix shouldBe "RULES FOR extract"
                }
            }

            "two bad replies fail the step in the open, and the answer is kept but nothing is added to the graph" {
                runBlocking {
                    val world = world("extract" to listOf("nope", "{\"actions\": 3}"))
                    val failure = shouldThrow<LlmStepFailed> { world.core.ingestAnswer.ingest(FIRST, null) }
                    failure.step shouldBe "extract"
                    world.store.rawEntries() shouldBe 1
                    world.core.showGraph.show().nodes shouldBe emptyList()
                }
            }

            "what the answer does not contain is dropped and said, and what it does contain is kept" {
                runBlocking {
                    val reply =
                        """{"company":{"n":"Acme","q":"в Acme"},
                            "role":{"title":"Backend Engineer","q":"бэкенд-инженером с 2022"},
                            "actions":[
                              {"kind":"built","verb":"implemented","what":"a catalog cache","q":"Внедрил кеш на Redis"},
                              {"kind":"built","verb":"migrated","what":"to Kubernetes","q":"перевёл всё на Kubernetes"},
                              {"kind":"wizardry","verb":"cast","what":"a spell","q":"Внедрил кеш"}],
                            "outcomes":[{"what":"load","val":40,"unit":"%","q":"Внедрил кеш на Redis"}]}"""
                    val world = world("extract" to listOf(reply))
                    val result = world.core.ingestAnswer.ingest(FIRST, null)
                    result.nodes.filter { it.type == "action" }.map { it.label } shouldBe listOf("implemented a catalog cache")
                    result.nodes.none { it.type == "outcome" } shouldBe true
                    world.diagnostics.dropped().size shouldBe 3
                    world.diagnostics.dropped().any { it.contains("перевёл всё на Kubernetes") } shouldBe true
                    world.diagnostics.dropped().any { it.contains("wizardry") } shouldBe true
                    world.diagnostics.dropped().any { it.startsWith("outcome") && it.contains("40") } shouldBe true
                }
            }

            "an answer that does not name the company cannot start a job" {
                runBlocking {
                    val world = world("extract" to listOf("""{"company":{"n":"Acme","q":"в компании Acme"}}"""))
                    shouldThrow<AnswerNotUsable> { world.core.ingestAnswer.ingest(FIRST, null) }
                    world.core.showGraph.show().nodes shouldBe emptyList()
                }
            }

            "an answer to a question that is not the pending one is refused and saves nothing" {
                runBlocking {
                    val world = world("extract" to listOf(FIRST_REPLY))
                    val first = world.core.ingestAnswer.ingest(FIRST, null)
                    val saved = world.store.rawEntries()
                    shouldThrow<AnswerNotUsable> { world.core.ingestAnswer.ingest("2022", QuestionId(Uuid.random())) }
                    world.store.rawEntries() shouldBe saved
                    world.core.nextQuestion.current().id shouldBe first.nextQuestion.id
                }
            }

            "an answer replayed to a question that was already answered does not move the interview" {
                runBlocking {
                    val dates = """{"role":{"start":"2022","current":true,"q":"с 2022"}}"""
                    val world = world("extract" to listOf(FIRST_REPLY, dates))
                    val first = world.core.ingestAnswer.ingest(FIRST, null)
                    val questionId = QuestionId(Uuid.parse(first.nextQuestion.id))
                    val second = world.core.ingestAnswer.ingest("Работаю с 2022", questionId)
                    val saved = world.store.rawEntries()
                    shouldThrow<AnswerNotUsable> { world.core.ingestAnswer.ingest("Работаю с 2022", questionId) }
                    world.store.rawEntries() shouldBe saved
                    world.core.nextQuestion.current().id shouldBe second.nextQuestion.id
                }
            }

            "a blank answer is refused before anything is saved" {
                runBlocking {
                    val world = world()
                    shouldThrow<AnswerNotUsable> { world.core.ingestAnswer.ingest("   ", null) }
                    world.store.rawEntries() shouldBe 0
                }
            }

            "a technology is saved under its canonical name however the model spelled it" {
                runBlocking {
                    val reply = FIRST_REPLY.replace("\"n\":\"Redis\",\"q\":\"Redis\"", "\"n\":\"редис\",\"q\":\"Redis\"")
                    val result = world("extract" to listOf(reply)).core.ingestAnswer.ingest(FIRST, null)
                    result.nodes.filter { it.type == "technology" }.map { it.label } shouldBe listOf("Redis")
                }
            }

            "nothing more to the question about alternatives is an answer, and the question is not asked again" {
                runBlocking {
                    val world = world("extract" to listOf(FIRST_REPLY))
                    var next = world.core.ingestAnswer.ingest(FIRST, null).nextQuestion
                    next.gap shouldBe "NO_DECISION"
                    next = world.core.ingestAnswer.ingest("нет", null).nextQuestion
                    next.gap shouldBe "NO_COLLAB"
                    next = world.core.ingestAnswer.ingest("Ничего", null).nextQuestion
                    next.gap shouldBe "NO_METRIC"
                }
            }

            "no metric is a result that is not a metric, and the question is closed by it" {
                runBlocking {
                    val world = world("extract" to listOf(FIRST_REPLY))
                    world.core.ingestAnswer.ingest(FIRST, null).nextQuestion.gap shouldBe "NO_DECISION"
                    world.core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "NO_COLLAB"
                    world.core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "NO_METRIC"
                    val result = world.core.ingestAnswer.ingest("no metric", null)
                    result.nodes.map { it.type to it.label } shouldContain ("outcome" to "result not measured")
                    result.nextQuestion.gap shouldBe "MORE_IN_PROJECT"
                    world.store.quotesOfNode(result.nodes.single { it.type == "outcome" }.id) shouldContainExactly listOf("no metric")
                }
            }

            "an answer to the open question goes into the same project" {
                runBlocking {
                    val more =
                        """{"actions":[{"kind":"improved","verb":"tuned","what":"cache expiry","q":"учил двух джунов"}]}"""
                    val world = world("extract" to listOf(FIRST_REPLY, more))
                    world.core.ingestAnswer.ingest(FIRST, null)
                    repeat(2) { world.core.ingestAnswer.ingest("нет", null) }
                    world.core.ingestAnswer.ingest("no metric", null).nextQuestion.gap shouldBe "MORE_IN_PROJECT"
                    val result = world.core.ingestAnswer.ingest("Ещё учил двух джунов", null)
                    result.nextQuestion.gap shouldBe "MORE_IN_PROJECT"
                    world.core.showGraph.show().nodes.count { it.type == "project" } shouldBe 1
                    world.core.showGraph.show().nodes.count { it.type == "action" } shouldBe 2
                }
            }

            "a second project in the role is its own project, and a job before is its own company" {
                runBlocking {
                    val second =
                        """{"project":{"title":"Importer","problem":"Manual imports","q":"писал импортёр"},
                            "actions":[{"kind":"built","verb":"wrote","what":"a cache warmer","q":"писал импортёр"}]}"""
                    val before =
                        """{"company":{"n":"Initech","q":"в Initech"},
                            "role":{"title":"Developer","start":"2018","end":"2021","q":"в Initech разработчиком"},
                            "actions":[{"kind":"built","verb":"built","what":"cached reports","q":"делал отчёты"}]}"""
                    val world =
                        world(
                            "extract" to listOf(FIRST_REPLY, second, before),
                            "bullets" to
                                listOf(
                                    """{"bullets":[{"text":"Implemented a catalog cache using Redis.","a":["A1"]}]}""",
                                    """{"bullets":[{"text":"Wrote a cache warmer.","a":["A1"]}]}""",
                                ),
                        )
                    val core = world.core
                    core.ingestAnswer.ingest(FIRST, null)
                    repeat(2) { core.ingestAnswer.ingest("нет", null) }
                    core.ingestAnswer.ingest("no metric", null)
                    core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "MORE_IN_ROLE"

                    core.ingestAnswer.ingest("Да, писал импортёр", null).nextQuestion.gap shouldBe "NO_DECISION"
                    core.showGraph.show().nodes.filter { it.type == "project" } shouldHaveSize 2
                    core.showGraph.show().nodes.filter { it.type == "role" } shouldHaveSize 1

                    repeat(2) { core.ingestAnswer.ingest("нет", null) }
                    core.ingestAnswer.ingest("no metric", null).nextQuestion.gap shouldBe "MORE_IN_PROJECT"
                    core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "MORE_IN_ROLE"
                    core.ingestAnswer.ingest("нет", null).nextQuestion.let {
                        it.gap shouldBe "PREVIOUS_ROLE"
                        it.text shouldBe "What did you do before Acme?"
                    }
                    core.ingestAnswer.ingest("Раньше в Initech разработчиком делал отчёты", null).nextQuestion.gap shouldBe "PROJECT_TITLE"
                    core.showGraph.show().nodes.filter { it.type == "company" }.map { it.label } shouldContainExactly listOf("Acme", "Initech")
                }
            }

            "nothing more to the question about earlier jobs ends the interview" {
                runBlocking {
                    val bullets = """{"bullets":[{"text":"Implemented a catalog cache using Redis.","a":["A1"]}]}"""
                    val world = world("extract" to listOf(FIRST_REPLY), "bullets" to listOf(bullets))
                    val core = world.core
                    core.ingestAnswer.ingest(FIRST, null)
                    repeat(2) { core.ingestAnswer.ingest("нет", null) }
                    core.ingestAnswer.ingest("no metric", null)
                    core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "MORE_IN_ROLE"
                    core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "PREVIOUS_ROLE"
                    core.ingestAnswer.ingest("нет", null).nextQuestion.gap shouldBe "DONE"
                }
            }

            "an action close in meaning to an old one is offered as a merge, and accepting folds it into the old one" {
                runBlocking {
                    val again =
                        """{"actions":[{"kind":"built","verb":"implemented","what":"a catalog cache","techs":[{"n":"Redis","q":"Redis"}],"q":"снова внедрил кеш на Redis"}]}"""
                    val world = world("extract" to listOf(FIRST_REPLY, again))
                    val core = world.core
                    core.ingestAnswer.ingest(FIRST, null)
                    core.ingestAnswer.ingest("Ещё раз: снова внедрил кеш на Redis", null).let { result ->
                        val candidate = result.mergeCandidate!!
                        candidate.newLabel shouldBe "implemented a catalog cache"
                        candidate.existingLabel shouldBe "implemented a catalog cache"
                        core.showGraph.show().nodes.count { it.type == "action" } shouldBe 2

                        core.resolveMerge.resolve(
                            ActionId(Uuid.parse(candidate.newActionId)),
                            ActionId(Uuid.parse(candidate.existingActionId)),
                            accept = true,
                        )
                        core.showGraph.show().nodes.count { it.type == "action" } shouldBe 1
                        world.store.quotesOfNode(candidate.existingActionId) shouldHaveSize 2
                    }
                }
            }

            "declining the merge leaves both actions" {
                runBlocking {
                    val again =
                        """{"actions":[{"kind":"built","verb":"implemented","what":"a catalog cache","q":"снова внедрил кеш на Redis"}]}"""
                    val world = world("extract" to listOf(FIRST_REPLY, again))
                    val core = world.core
                    core.ingestAnswer.ingest(FIRST, null)
                    val candidate = core.ingestAnswer.ingest("Ещё раз: снова внедрил кеш на Redis", null).mergeCandidate!!
                    core.resolveMerge.resolve(
                        ActionId(Uuid.parse(candidate.newActionId)),
                        ActionId(Uuid.parse(candidate.existingActionId)),
                        accept = false,
                    )
                    core.showGraph.show().nodes.count { it.type == "action" } shouldBe 2
                }
            }

            "actions that are not alike are not offered as a merge" {
                runBlocking {
                    val other =
                        """{"actions":[{"kind":"investigated","verb":"profiled","what":"database queries","q":"профилировал запросы"}]}"""
                    val world = world("extract" to listOf(FIRST_REPLY, other))
                    world.core.ingestAnswer.ingest(FIRST, null)
                    world.core.ingestAnswer.ingest("Ещё профилировал запросы", null).mergeCandidate shouldBe null
                }
            }

            "an action no skill is near is tagged by the model from the nearest candidates, and a skill it invents is not a skill" {
                runBlocking {
                    val reply =
                        """{"company":{"n":"Acme","q":"в Acme"},
                            "actions":[{"kind":"built","verb":"wrote","what":"build scripts","q":"Внедрил кеш на Redis"}]}"""
                    val tags = """{"skills":["ci-cd","telepathy"]}"""
                    val llm = ScriptedLlm.of("extract" to listOf(reply), "tags" to listOf(tags))
                    val world = DemoWorld(llm)
                    val result = world.core.ingestAnswer.ingest(FIRST, null)
                    llm.callsTo("tags") shouldBe 1
                    result.nodes.filter { it.type == "skill" }.map { it.label } shouldBe listOf("CI/CD")
                    world.diagnostics.unmapped() shouldBe listOf("skill: telepathy")
                }
            }

            "the model sees names only, with the question and the answer, and not the ids of the store" {
                runBlocking {
                    val llm = ScriptedLlm.of("extract" to listOf(FIRST_REPLY, "{}"))
                    val core = DemoWorld(llm).core
                    core.ingestAnswer.ingest(FIRST, null)
                    core.ingestAnswer.ingest("Начал в 2022", null)
                    val suffix = llm.promptsOf("extract")[1].dynamicSuffix
                    suffix shouldContain "company=\"Acme\"; role=\"Backend Engineer\"; project=\"Catalog cache\""
                    suffix shouldContain "Question asked: What alternatives did you consider, and why did you choose Redis?"
                    suffix shouldContain "Начал в 2022"
                    Regex("[0-9a-f]{8}-[0-9a-f]{4}-").containsMatchIn(suffix) shouldBe false
                }
            }

            "a reset forgets everything and starts the interview again" {
                runBlocking {
                    val world = world("extract" to listOf(FIRST_REPLY))
                    world.core.ingestAnswer.ingest(FIRST, null)
                    world.core.resetData.reset()
                    world.core.showGraph.show().nodes shouldBe emptyList()
                    world.core.nextQuestion.current().gap shouldBe "OPENING"
                    world.store.rawEntries() shouldBe 0
                }
            }
        },
    )
