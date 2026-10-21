package tallyvane.knowledge.infra.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tallyvane.knowledge.domain.port.CachedPrompt

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private const val REPLY =
    """{"id":"msg_1","type":"message","role":"assistant","model":"m","stop_reason":"end_turn",
        "content":[{"type":"text","text":"{\"ok\":true}"}],
        "usage":{"input_tokens":120,"cache_creation_input_tokens":900,"cache_read_input_tokens":0,"output_tokens":42}}"""

private fun client(engine: MockEngine) = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

class AnthropicClientSpec :
    StringSpec(
        {
            "the stable part of the prompt is sent first as a cached system block, and the changing part as the message" {
                runBlocking {
                    var body: JsonObject? = null
                    var key: String? = null
                    var version: String? = null
                    val engine =
                        MockEngine { request ->
                            body = Json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
                            key = request.headers["x-api-key"]
                            version = request.headers["anthropic-version"]
                            respond(REPLY, HttpStatusCode.OK, JSON_HEADERS)
                        }
                    AnthropicClient(client(engine), "secret", "claude-test").complete(CachedPrompt("extract", "RULES", "ANSWER", 512))

                    key shouldBe "secret"
                    version shouldBe "2023-06-01"
                    val sent = body!!
                    sent["model"]!!.jsonPrimitive.content shouldBe "claude-test"
                    sent["max_tokens"]!!.jsonPrimitive.content shouldBe "512"
                    val system = sent["system"]!!.jsonArray.single().jsonObject
                    system["text"]!!.jsonPrimitive.content shouldBe "RULES"
                    system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content shouldBe "ephemeral"
                    val message = sent["messages"]!!.jsonArray.single().jsonObject
                    message["role"]!!.jsonPrimitive.content shouldBe "user"
                    message["content"]!!.jsonPrimitive.content shouldBe "ANSWER"
                    (sent["system"] is JsonArray) shouldBe true
                }
            }

            "the reply carries the text and the four counts the API reports" {
                runBlocking {
                    val engine = MockEngine { respond(REPLY, HttpStatusCode.OK, JSON_HEADERS) }
                    val reply = AnthropicClient(client(engine), "k", "m").complete(CachedPrompt("extract", "p", "s"))
                    reply.text shouldBe """{"ok":true}"""
                    reply.usage.inputTokens shouldBe 120
                    reply.usage.cacheCreationTokens shouldBe 900
                    reply.usage.cacheReadTokens shouldBe 0
                    reply.usage.outputTokens shouldBe 42
                }
            }

            "an overloaded API is tried again, and a reply that follows is used" {
                runBlocking {
                    var calls = 0
                    val engine =
                        MockEngine {
                            if (++calls < 3) {
                                respond("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""", HttpStatusCode(529, "Overloaded"), JSON_HEADERS)
                            } else {
                                respond(REPLY, HttpStatusCode.OK, JSON_HEADERS)
                            }
                        }
                    AnthropicClient(client(engine), "k", "m", retryDelays = listOf(0, 0)).complete(CachedPrompt("tags", "p", "s")).text shouldBe """{"ok":true}"""
                    calls shouldBe 3
                }
            }

            "a gateway timeout is tried again like any other passing failure" {
                runBlocking {
                    var calls = 0
                    val engine =
                        MockEngine {
                            if (++calls < 2) {
                                respond("gateway timeout", HttpStatusCode.GatewayTimeout, JSON_HEADERS)
                            } else {
                                respond(REPLY, HttpStatusCode.OK, JSON_HEADERS)
                            }
                        }
                    AnthropicClient(client(engine), "k", "m", retryDelays = listOf(0, 0)).complete(CachedPrompt("tags", "p", "s")).text shouldBe """{"ok":true}"""
                    calls shouldBe 2
                }
            }

            "a refusal is not tried again, and the API's own words are given back" {
                runBlocking {
                    var calls = 0
                    val engine =
                        MockEngine {
                            calls++
                            respond("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""", HttpStatusCode.Unauthorized, JSON_HEADERS)
                        }
                    val failure = shouldThrow<LlmCallFailed> { AnthropicClient(client(engine), "k", "m").complete(CachedPrompt("tags", "p", "s")) }
                    failure.message shouldContain "401"
                    failure.message shouldContain "invalid x-api-key"
                    calls shouldBe 1
                }
            }

            "a reply with no text is a failure and not an empty answer" {
                runBlocking {
                    val engine = MockEngine { respond("""{"content":[],"stop_reason":"max_tokens","usage":{}}""", HttpStatusCode.OK, JSON_HEADERS) }
                    shouldThrow<LlmCallFailed> { AnthropicClient(client(engine), "k", "m").complete(CachedPrompt("tags", "p", "s")) }
                        .message shouldContain "max_tokens"
                }
            }
        },
    )
