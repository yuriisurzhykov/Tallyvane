package tallyvane.knowledge.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.fixtures.DemoScript
import tallyvane.knowledge.fixtures.DemoWorld
import tallyvane.knowledge.fixtures.ScriptedLlm
import kotlin.uuid.Uuid

private suspend fun closedProject(world: DemoWorld) {
    world.core.ingestAnswer.ingest(DemoScript.FIRST_ANSWER, null)
    world.core.ingestAnswer.ingest(DemoScript.DATES_ANSWER, null)
    world.core.ingestAnswer.ingest(DemoScript.TITLE_ANSWER, null)
    world.core.ingestAnswer.ingest(DemoScript.DECISION_ANSWER, null)
    world.core.ingestAnswer.ingest(DemoScript.NOTHING_MORE, null)
}

private fun scripted(bullets: List<String>, vacancy: List<String> = listOf(DemoScript.VACANCY)) =
    ScriptedLlm.of("extract" to DemoScript.EXTRACT, "bullets" to bullets, "vacancy" to vacancy)

private fun idOf(text: String) = VacancyId(Uuid.parse(text))

class ClosingAndVacancySpec :
    StringSpec(
        {
            "a bullet with a figure the person never gave is sent back once, and the corrected one is stored" {
                runBlocking {
                    val bad = DemoScript.BULLETS.replace("about 40%", "60%")
                    val llm = scripted(listOf(bad, DemoScript.BULLETS))
                    val world = DemoWorld(llm)
                    closedProject(world)
                    llm.callsTo("bullets") shouldBe 2
                    llm.promptsOf("bullets")[1].dynamicSuffix shouldContain "The figure 60.0 is not among the facts"
                    val vacancy = world.core.parseVacancy.parse(DemoScript.VACANCY_TEXT)
                    world.core.buildResume.build(idOf(vacancy.vacancyId)).markdown shouldContain "Cut database load by about 40%"
                    world.diagnostics.dropped() shouldBe emptyList()
                }
            }

            "a bullet that names a technology the project never used is refused" {
                runBlocking {
                    val bad = DemoScript.BULLETS.replace("a Redis cache", "a Kafka-fed Redis cache")
                    val llm = scripted(listOf(bad, DemoScript.BULLETS))
                    val world = DemoWorld(llm)
                    closedProject(world)
                    llm.promptsOf("bullets")[1].dynamicSuffix shouldContain "Kafka"
                }
            }

            "two refused proposals are replaced by a bullet built from the facts, so the project still closes with true words" {
                runBlocking {
                    val bad = DemoScript.BULLETS.replace("about 40%", "60%")
                    val world = DemoWorld(scripted(listOf(bad, bad)))
                    closedProject(world)
                    world.diagnostics.dropped().single() shouldContain "the template is used instead"
                    val vacancy = world.core.parseVacancy.parse(DemoScript.VACANCY_TEXT)
                    val resume = world.core.buildResume.build(idOf(vacancy.vacancyId))
                    resume.bullets.map { it.text } shouldBe
                        listOf(
                            "Implemented the cache using Redis, reducing database load by about 40%.",
                            "Proposed a Redis cache for the catalog using Redis.",
                        )
                }
            }

            "a bullet that covers an action that is not there is refused" {
                runBlocking {
                    val bad = DemoScript.BULLETS.replace("\"A5\"", "\"A9\"")
                    val llm = scripted(listOf(bad, DemoScript.BULLETS))
                    closedProject(DemoWorld(llm))
                    llm.promptsOf("bullets")[1].dynamicSuffix shouldContain "A9"
                }
            }

            "a vacancy skill the product does not know is moved to unmapped, and a repeated skill is merged" {
                runBlocking {
                    val reply =
                        """{"title":"Engineer","reqs":[
                            {"skill":"caching","importance":1,"must":false},
                            {"skill":"caching","importance":9,"must":true},
                            {"skill":"graphql","importance":2,"must":true}],"unmapped":["Terraform"]}"""
                    val world = DemoWorld(scripted(listOf(DemoScript.BULLETS), listOf(reply)))
                    val view = world.core.parseVacancy.parse("Engineer who knows caching, GraphQL and Terraform")
                    view.reqs.map { Triple(it.skill, it.importance, it.must) } shouldBe listOf(Triple("caching", 3, true))
                    view.unmapped shouldBe listOf("Terraform", "graphql")
                    world.diagnostics.unmapped() shouldBe listOf("skill: graphql")
                }
            }

            "a vacancy reply that is not what was asked for fails in the open after one retry" {
                runBlocking {
                    val world = DemoWorld(scripted(emptyList(), listOf("hmm", "{\"reqs\": \"all of them\"}")))
                    val failure = shouldThrow<LlmStepFailed> { world.core.parseVacancy.parse("Engineer") }
                    failure.step shouldBe "vacancy"
                }
            }

            "the vacancy prompt carries the whole skill list after the rules, so that it can be cached" {
                runBlocking {
                    val llm = scripted(emptyList())
                    DemoWorld(llm).core.parseVacancy.parse("Engineer")
                    val prompt = llm.promptsOf("vacancy").single()
                    prompt.cachedPrefix shouldContain "RULES FOR vacancy"
                    prompt.cachedPrefix shouldContain "kafka: Kafka"
                    prompt.cachedPrefix shouldContain "cross-team-collaboration: Cross-team collaboration"
                    prompt.dynamicSuffix shouldContain "Engineer"
                }
            }

            "a résumé has no projects that were never closed" {
                runBlocking {
                    val world = DemoWorld(scripted(emptyList()))
                    world.core.ingestAnswer.ingest(DemoScript.FIRST_ANSWER, null)
                    val vacancy = world.core.parseVacancy.parse(DemoScript.VACANCY_TEXT)
                    val resume = world.core.buildResume.build(idOf(vacancy.vacancyId))
                    resume.bullets shouldBe emptyList()
                    resume.markdown shouldContain "## Experience"
                    resume.markdown.contains("### Acme") shouldBe false
                }
            }

            "the skills of a résumé put those the vacancy asks for first" {
                runBlocking {
                    val world = DemoWorld(scripted(listOf(DemoScript.BULLETS), listOf("""{"title":"T","reqs":[{"skill":"cross-team-collaboration","importance":1,"must":false}]}""")))
                    closedProject(world)
                    val vacancy = world.core.parseVacancy.parse("Teamwork")
                    val skills = world.core.buildResume.build(idOf(vacancy.vacancyId)).markdown.substringAfter("## Skills\n")
                    skills.startsWith("Cross-team collaboration") shouldBe true
                }
            }
        },
    )
