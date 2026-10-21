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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tallyvane.knowledge.domain.Embedding

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private fun client(engine: MockEngine) = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

class OllamaEmbeddingsSpec :
    StringSpec(
        {
            "the texts are sent in one request to /api/embed and the vectors come back in the same order" {
                runBlocking {
                    var path: String? = null
                    var sent = JsonObject(emptyMap())
                    val engine =
                        MockEngine { request ->
                            path = request.url.encodedPath
                            sent = Json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
                            respond("""{"embeddings":[[1.0,0.0],[0.0,1.0]]}""", HttpStatusCode.OK, JSON_HEADERS)
                        }
                    val vectors = OllamaEmbeddings(client(engine), "http://ollama:11434/", "nomic-embed-text").embed(listOf("a", "b"))

                    path shouldBe "/api/embed"
                    sent["model"]!!.jsonPrimitive.content shouldBe "nomic-embed-text"
                    sent["input"]!!.jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("a", "b")
                    vectors.size shouldBe 2
                    vectors[0].similarityTo(Embedding(floatArrayOf(1f, 0f))) shouldBe 1.0
                    vectors[1].similarityTo(Embedding(floatArrayOf(1f, 0f))) shouldBe 0.0
                }
            }

            "no texts make no request" {
                runBlocking {
                    val engine = MockEngine { error("No request is expected.") }
                    OllamaEmbeddings(client(engine), "http://ollama", "m").embed(emptyList()) shouldBe emptyList()
                }
            }

            "fewer vectors than texts is a failure" {
                runBlocking {
                    val engine = MockEngine { respond("""{"embeddings":[[1.0]]}""", HttpStatusCode.OK, JSON_HEADERS) }
                    shouldThrow<EmbeddingFailed> { OllamaEmbeddings(client(engine), "http://ollama", "m").embed(listOf("a", "b")) }
                }
            }

            "a model Ollama does not have is said in Ollama's words" {
                runBlocking {
                    val engine = MockEngine { respond("""{"error":"model \"nomic-embed-text\" not found, try pulling it first"}""", HttpStatusCode.NotFound, JSON_HEADERS) }
                    shouldThrow<EmbeddingFailed> { OllamaEmbeddings(client(engine), "http://ollama", "nomic-embed-text").embed(listOf("a")) }
                        .message shouldContain "not found"
                }
            }
        },
    )
