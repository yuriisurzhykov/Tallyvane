package tallyvane.platform.llm

import tallyvane.platform.kernel.Clock
import tallyvane.platform.llm.core.Model
import tallyvane.platform.llm.core.Speakers
import java.security.MessageDigest

/**
 * The model as the administrator chose it: asks the active provider with the model of the call's tier, answers a
 * question already asked from the memo for nothing (law 4), and records every call with its cost.
 */
public class RoutedModel internal constructor(
    private val providers: Providers,
    private val speakers: Speakers,
    private val memo: Memo,
    private val log: CallLog,
    private val clock: Clock,
) : Model {
    override suspend fun ask(call: ModelCall): ModelReply {
        val provider = providers.active() ?: return ModelReply.Unavailable(NONE)
        val model = provider.modelFor(call.tier)
        val key = keyOf(call, provider.memoPart(model))
        val known = memo.recall(key)
        val reply = if (known != null) {
            ModelReply.Answered(known, TokenCount(0, 0, 0), remembered = true)
        } else {
            provider.speakWith(speakers).ask(call, model)
        }
        if (reply is ModelReply.Answered) {
            if (known == null) memo.keep(key, call.spec, model, reply.text, reply.tokens)
            val cost = if (known == null) provider.costOf(reply.tokens) else 0.0
            log.record(clock.now(), call.onBehalfOf, call.spec, provider.id(), model, reply.tokens, cost, known != null)
        }
        return reply
    }

    private fun keyOf(call: ModelCall, where: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(call.spec, where, call.instructions, call.input).forEach { part ->
            digest.update(part.toByteArray())
            digest.update(SEPARATOR)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    override fun toString(): String = "RoutedModel($providers)"

    private companion object {
        const val SEPARATOR: Byte = 0
        const val NONE = "No model provider is chosen. An administrator chooses one in Admin, Models."
    }
}
