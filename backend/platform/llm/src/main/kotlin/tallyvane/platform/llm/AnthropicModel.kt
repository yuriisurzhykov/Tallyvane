package tallyvane.platform.llm

import com.anthropic.client.AnthropicClient
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.optionals.getOrNull

/**
 * [Model] over the Anthropic Messages API, through the official SDK.
 *
 * The instructions of a spec go first and are marked for the provider's prompt cache; the input follows, so
 * calls of one spec share a prefix. Effort is low: every spec asks for reading or for one short piece of
 * writing, and the backend checks the reply anyway. A reply that was refused or cut off is not an answer.
 */
internal class AnthropicModel(private val client: AnthropicClient, private val names: ModelNames) : Model {
    override suspend fun ask(call: ModelCall): ModelReply = try {
        replyOf(withContext(Dispatchers.IO) { client.messages().create(paramsOf(call)) })
    } catch (failure: AnthropicException) {
        ModelReply.Unavailable(failure.message ?: failure.javaClass.simpleName)
    }

    private fun paramsOf(call: ModelCall): MessageCreateParams = MessageCreateParams.builder()
        .model(names.of(call.tier))
        .maxTokens(MAX_TOKENS)
        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
        .systemOfTextBlockParams(
            listOf(
                TextBlockParam.builder()
                    .text(call.instructions)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build(),
            ),
        )
        .addUserMessage(call.input)
        .build()

    private fun replyOf(message: Message): ModelReply {
        val stopped = message.stopReason().getOrNull()?.asString()
        if (stopped != "end_turn") {
            return ModelReply.Unavailable("The model stopped with $stopped.")
        }
        val text = message.content().mapNotNull { block -> block.text().getOrNull()?.text() }.joinToString("")
        val usage = message.usage()
        val cached = usage.cacheReadInputTokens().getOrNull() ?: 0
        val written = usage.cacheCreationInputTokens().getOrNull() ?: 0
        return ModelReply.Answered(
            text,
            TokenCount(usage.inputTokens() + cached + written, usage.outputTokens(), cached),
            remembered = false,
        )
    }

    override fun toString(): String = "AnthropicModel($names)"

    private companion object {
        /**
         * Room for the longest reply a spec allows, and under what a call without streaming may ask for.
         */
        const val MAX_TOKENS = 8_000L
    }
}
