package tallyvane.knowledge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tallyvane.knowledge.fixtures.DemoScript
import tallyvane.knowledge.fixtures.DemoWorld
import tallyvane.knowledge.fixtures.ScriptedLlm

private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) =
    client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

private fun text(value: String) = Json.encodeToString(kotlinx.serialization.serializer<String>(), value)

private fun serving(world: DemoWorld, test: suspend ApplicationTestBuilder.() -> Unit) =
    testApplication {
        application { Api(world.core) { emptyList() }.install(this) }
        test()
    }

class ApiSpec :
    StringSpec(
        {
            "the page is served from the root" {
                serving(DemoWorld(DemoScript.llm())) {
                    val reply = client.get("/")
                    reply.status shouldBe HttpStatusCode.OK
                    reply.bodyAsText() shouldContain "Knowledge Core"
                }
            }

            "the first question is the opening one" {
                serving(DemoWorld(DemoScript.llm())) {
                    val reply = client.get("/api/question")
                    reply.status shouldBe HttpStatusCode.OK
                    json(reply.bodyAsText())["gap"]!!.jsonPrimitive.content shouldBe "OPENING"
                }
            }

            "an answer is answered with the nodes it made and the next question" {
                serving(DemoWorld(DemoScript.llm())) {
                    val reply = postJson("/api/answer", """{"text":${text(DemoScript.FIRST_ANSWER)}}""")
                    reply.status shouldBe HttpStatusCode.OK
                    val body = json(reply.bodyAsText())
                    body["nodes"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }.toSet() shouldBe
                        setOf("company", "role", "project", "action", "technology", "collaborator", "outcome", "skill")
                    body["nextQuestion"]!!.jsonObject["gap"]!!.jsonPrimitive.content shouldBe "ROLE_DATES"
                    body["mergeCandidate"]!!.toString() shouldBe "null"
                }
            }

            "an answer to a question that is not the pending one is refused with 422 and the reason" {
                serving(DemoWorld(DemoScript.llm())) {
                    val reply = postJson("/api/answer", """{"text":"да","questionId":"00000000-0000-4000-8000-000000000001"}""")
                    reply.status shouldBe HttpStatusCode.UnprocessableEntity
                    json(reply.bodyAsText())["error"]!!.jsonPrimitive.content shouldContain "not the one waiting"
                }
            }

            "a body that is not JSON for the endpoint is a 400, not a 500" {
                serving(DemoWorld(DemoScript.llm())) {
                    val reply = postJson("/api/answer", "{not json")
                    reply.status shouldBe HttpStatusCode.BadRequest
                    json(reply.bodyAsText())["error"]!!.jsonPrimitive.content shouldContain "could not be read"
                }
            }

            "a model that cannot be understood twice is a 502 that names the step" {
                serving(DemoWorld(ScriptedLlm.of("extract" to listOf("nope", "still nope")))) {
                    val reply = postJson("/api/answer", """{"text":${text(DemoScript.FIRST_ANSWER)}}""")
                    reply.status shouldBe HttpStatusCode.BadGateway
                    json(reply.bodyAsText())["step"]!!.jsonPrimitive.content shouldBe "extract"
                }
            }

            "the graph and the words behind a node can be read" {
                serving(DemoWorld(DemoScript.llm())) {
                    postJson("/api/answer", """{"text":${text(DemoScript.FIRST_ANSWER)}}""")
                    val graph = json(client.get("/api/graph").bodyAsText())
                    val company = graph["nodes"]!!.jsonArray.map { it.jsonObject }.single { it["type"]!!.jsonPrimitive.content == "company" }
                    val evidence = json(client.get("/api/evidence/${company["id"]!!.jsonPrimitive.content}").bodyAsText())
                    evidence["quotes"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("в компании Acme")
                }
            }

            "a vacancy is parsed, matched and turned into a resume" {
                serving(DemoWorld(DemoScript.llm())) {
                    DemoScript.ANSWERS.forEach { postJson("/api/answer", """{"text":${text(it)}}""") }
                    val vacancy = json(postJson("/api/vacancy", """{"text":${text(DemoScript.VACANCY_TEXT)}}""").bodyAsText())
                    val id = vacancy["vacancyId"]!!.jsonPrimitive.content
                    vacancy["unmapped"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("GraphQL")

                    val match = json(client.get("/api/match?vacancyId=$id").bodyAsText())
                    match["gaps"]!!.jsonArray.map { it.jsonObject }
                        .single { it["skill"]!!.jsonPrimitive.content == "kafka" }["missing"]!!.jsonPrimitive.content shouldBe "true"

                    val resume = json(client.get("/api/resume?vacancyId=$id").bodyAsText())
                    resume["markdown"]!!.jsonPrimitive.content shouldContain "Acme"
                    resume["bullets"]!!.jsonArray.single().jsonObject["evidence"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainAll
                        listOf("Предложил кеш на Redis", "согласовал с DBA", "Нагрузка на базу упала примерно на 40%")
                }
            }

            "a vacancy id that is not an id is a 400 and one that is unknown is a 404" {
                serving(DemoWorld(DemoScript.llm())) {
                    client.get("/api/match?vacancyId=nope").status shouldBe HttpStatusCode.BadRequest
                    client.get("/api/match").status shouldBe HttpStatusCode.BadRequest
                    client.get("/api/match?vacancyId=00000000-0000-4000-8000-000000000001").status shouldBe HttpStatusCode.NotFound
                }
            }

            "a reset empties the graph and the interview starts over" {
                serving(DemoWorld(DemoScript.llm())) {
                    postJson("/api/answer", """{"text":${text(DemoScript.FIRST_ANSWER)}}""")
                    client.post("/api/reset").status shouldBe HttpStatusCode.NoContent
                    json(client.get("/api/graph").bodyAsText())["nodes"]!!.jsonArray.size shouldBe 0
                    json(client.get("/api/question").bodyAsText())["gap"]!!.jsonPrimitive.content shouldBe "OPENING"
                }
            }

            "the calls to the model can be read" {
                testApplication {
                    application { Api(DemoWorld(DemoScript.llm()).core) { emptyList() }.install(this) }
                    val reply = client.get("/api/debug/llm")
                    reply.status shouldBe HttpStatusCode.OK
                    reply.bodyAsText() shouldBe "[]"
                }
            }
        },
    )
