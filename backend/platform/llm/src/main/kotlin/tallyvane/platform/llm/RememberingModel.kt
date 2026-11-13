package tallyvane.platform.llm

import java.security.MessageDigest

/**
 * A [Model] that asks its memo first, and keeps what the model answers.
 *
 * The key is the SHA-256 of the spec, the model's name, the instructions and the input, so a new version of a
 * spec, another model or one changed character is another question.
 */
public class RememberingModel(private val inner: Model, private val memo: Memo, private val names: ModelNames) :
    Model {
    override suspend fun ask(call: ModelCall): ModelReply {
        val model = names.of(call.tier)
        val key = keyOf(call, model)
        val known = memo.recall(key)
        if (known != null) {
            return ModelReply.Answered(known, TokenCount(0, 0, 0), remembered = true)
        }
        val reply = inner.ask(call)
        if (reply is ModelReply.Answered) {
            memo.keep(key, call.spec, model, reply.text, reply.tokens)
        }
        return reply
    }

    private fun keyOf(call: ModelCall, model: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(call.spec, model, call.instructions, call.input).forEach { part ->
            digest.update(part.toByteArray())
            digest.update(SEPARATOR)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    override fun toString(): String = "RememberingModel($inner)"

    private companion object {
        const val SEPARATOR: Byte = 0
    }
}
