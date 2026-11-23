package tallyvane.knowledge.infra.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.port.EmbeddingProvider

/** The embedding model did not give vectors; [message] says why. */
class EmbeddingFailed(message: String) : Exception(message)

/** Embeddings from a local Ollama (`/api/embed`). No paid API stands behind the product's meaning-matching. */
class OllamaEmbeddings(
    private val http: HttpClient,
    private val baseUrl: String,
    private val model: String,
) : EmbeddingProvider {
    override suspend fun embed(texts: List<String>): List<Embedding> {
        if (texts.isEmpty()) return emptyList()
        val response =
            try {
                http.post("${baseUrl.trimEnd('/')}/api/embed") {
                    contentType(ContentType.Application.Json)
                    setBody(Request(model, texts))
                }
            } catch (e: java.io.IOException) {
                throw EmbeddingFailed("Ollama could not be reached at $baseUrl: ${e.message}")
            }
        if (!response.status.isSuccess()) {
            throw EmbeddingFailed("Ollama answered ${response.status.value}: ${response.bodyAsText().take(EXCERPT)}")
        }
        val vectors = response.body<Reply>().embeddings
        if (vectors.size != texts.size) throw EmbeddingFailed("Asked for ${texts.size} embeddings and got ${vectors.size}.")
        return vectors.map { Embedding(it.toFloatArray()) }
    }

    @Serializable
    private class Request(val model: String, val input: List<String>)

    @Serializable
    private class Reply(val embeddings: List<List<Float>> = emptyList())

    private companion object {
        const val EXCERPT = 300
    }
}
