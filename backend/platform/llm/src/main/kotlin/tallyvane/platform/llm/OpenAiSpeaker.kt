package tallyvane.platform.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import tallyvane.platform.kernel.Secret
import tallyvane.platform.llm.core.Speaker
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Any provider that speaks OpenAI's chat completions: `POST {baseUrl}/chat/completions`.
 */
internal class OpenAiSpeaker(private val http: HttpClient, baseUrl: String, private val key: Secret) : Speaker {
    private val address = URI.create(baseUrl.trimEnd('/') + "/chat/completions")

    override suspend fun ask(call: ModelCall, model: String): ModelReply = try {
        val response = withContext(Dispatchers.IO) {
            http.send(requestOf(call, model), HttpResponse.BodyHandlers.ofString())
        }
        if (response.statusCode() / HUNDRED == 2) {
            replyOf(Json.parseToJsonElement(response.body()).jsonObject)
        } else {
            ModelReply.Unavailable("The provider answered ${response.statusCode()}: ${response.body().take(LOGGED)}")
        }
    } catch (failure: IOException) {
        ModelReply.Unavailable(failure.message ?: failure.javaClass.simpleName)
    } catch (failure: SerializationException) {
        ModelReply.Unavailable("The provider's answer is not JSON: ${failure.message?.take(LOGGED)}")
    } catch (failure: IllegalArgumentException) {
        ModelReply.Unavailable("The provider's answer has no message: ${failure.message?.take(LOGGED)}")
    }

    private fun requestOf(call: ModelCall, model: String): HttpRequest {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", call.instructions)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", call.input)
                }
            }
        }
        return HttpRequest.newBuilder(address)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${key.revealed()}")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
    }

    private fun replyOf(answer: JsonObject): ModelReply {
        val choice = answer.getValue("choices").jsonArray.first().jsonObject
        val finished = choice["finish_reason"]?.jsonPrimitive?.content
        if (finished != "stop") return ModelReply.Unavailable("The model stopped with $finished.")
        val text = choice.getValue("message").jsonObject.getValue("content").jsonPrimitive.content
        val usage = answer["usage"]?.jsonObject
        val cached = usage?.get("prompt_tokens_details")?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.longOrNull
        return ModelReply.Answered(
            text,
            TokenCount(
                usage?.get("prompt_tokens")?.jsonPrimitive?.long ?: 0,
                usage?.get("completion_tokens")?.jsonPrimitive?.long ?: 0,
                cached ?: 0,
            ),
            remembered = false,
        )
    }

    override fun toString(): String = "OpenAiSpeaker($address)"

    private companion object {
        const val MAX_TOKENS = 8_000
        const val TIMEOUT_SECONDS = 120L
        const val HUNDRED = 100
        const val LOGGED = 300
    }
}
