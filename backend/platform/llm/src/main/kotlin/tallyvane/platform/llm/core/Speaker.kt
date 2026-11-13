package tallyvane.platform.llm.core

import tallyvane.platform.llm.ModelCall
import tallyvane.platform.llm.ModelReply

/**
 * Asks one provider's API one question, with a model named.
 */
internal interface Speaker {
    suspend fun ask(call: ModelCall, model: String): ModelReply
}
