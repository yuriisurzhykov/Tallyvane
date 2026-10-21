package tallyvane.knowledge.infra.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tallyvane.knowledge.domain.port.CachedPrompt
import tallyvane.knowledge.domain.port.LlmClient
import tallyvane.knowledge.domain.port.LlmReply
import tallyvane.knowledge.domain.port.LlmUsage

/** The call to the model did not give a reply; [message] says why, in words that can be shown to the person. */
class LlmCallFailed(message: String) : Exception(message)

/**
 * The Anthropic Messages API over HTTP. The part of the prompt that is the same on every call of a step goes first,
 * as a system block marked for caching, and the part that changes goes after it as the message; the API reads the
 * prefix from its cache on the next call within a few minutes, and reports what it read and what it wrote.
 *
 * A prefix shorter than the model's minimum for caching is simply not cached: the counts in the reply say so.
 */
class AnthropicClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val retryDelays: List<Long> = listOf(1_000, 4_000),
) : LlmClient {
    override suspend fun complete(prompt: CachedPrompt): LlmReply {
        val request =
            Request(
                model,
                prompt.maxTokens,
                listOf(SystemBlock("text", prompt.cachedPrefix, CacheControl("ephemeral"))),
                listOf(Message("user", prompt.dynamicSuffix)),
            )
        val started = System.nanoTime()
        val response = sendWithRetries(request)
        val reply = response.body<Response>()
        val text = reply.content.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }
        if (text.isBlank()) throw LlmCallFailed("The model replied with no text (stop reason: ${reply.stopReason}).")
        return LlmReply(
            text,
            LlmUsage(
                reply.usage.inputTokens,
                reply.usage.cacheCreationTokens,
                reply.usage.cacheReadTokens,
                reply.usage.outputTokens,
            ),
            (System.nanoTime() - started) / NANOS_PER_MILLI,
        )
    }

    private suspend fun sendWithRetries(request: Request): HttpResponse {
        var attempt = 0
        while (true) {
            val response =
                try {
                    http.post("$baseUrl/v1/messages") {
                        header("x-api-key", apiKey)
                        header("anthropic-version", API_VERSION)
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                } catch (e: java.io.IOException) {
                    throw LlmCallFailed("The model could not be reached: ${e.message}")
                }
            if (response.status.isSuccess()) return response
            if (response.status.value !in RETRYABLE || attempt >= retryDelays.size) throw failureOf(response)
            delay(retryDelays[attempt++])
        }
    }

    private suspend fun failureOf(response: HttpResponse): LlmCallFailed {
        val body = response.bodyAsText()
        val detail = runCatching { errors.decodeFromString<ErrorBody>(body).error.message }.getOrNull()
        return LlmCallFailed("The model answered ${response.status.value}: ${detail ?: body.take(ERROR_EXCERPT)}")
    }

    @Serializable
    private class CacheControl(val type: String)

    @Serializable
    private class SystemBlock(val type: String, val text: String, @SerialName("cache_control") val cacheControl: CacheControl)

    @Serializable
    private class Message(val role: String, val content: String)

    @Serializable
    private class Request(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val system: List<SystemBlock>,
        val messages: List<Message>,
    )

    @Serializable
    private class Block(val type: String, val text: String? = null)

    @Serializable
    private class Usage(
        @SerialName("input_tokens") val inputTokens: Int = 0,
        @SerialName("cache_creation_input_tokens") val cacheCreationTokens: Int = 0,
        @SerialName("cache_read_input_tokens") val cacheReadTokens: Int = 0,
        @SerialName("output_tokens") val outputTokens: Int = 0,
    )

    @Serializable
    private class Response(
        val content: List<Block> = emptyList(),
        @SerialName("stop_reason") val stopReason: String? = null,
        val usage: Usage = Usage(),
    )

    @Serializable
    private class ErrorDetail(val message: String = "")

    @Serializable
    private class ErrorBody(val error: ErrorDetail = ErrorDetail())

    private companion object {
        val errors = Json { ignoreUnknownKeys = true }
        const val API_VERSION = "2023-06-01"
        const val NANOS_PER_MILLI = 1_000_000L
        const val ERROR_EXCERPT = 300

        /** Rate limit, the API's own overload status, and the gateway errors that pass. */
        val RETRYABLE = setOf(429, 500, 502, 503, 529)
    }
}
