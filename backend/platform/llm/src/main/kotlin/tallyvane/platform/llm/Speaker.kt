package tallyvane.platform.llm

/**
 * Asks one provider's API one question, with a model named.
 */
internal interface Speaker {
    suspend fun ask(call: ModelCall, model: String): ModelReply
}
