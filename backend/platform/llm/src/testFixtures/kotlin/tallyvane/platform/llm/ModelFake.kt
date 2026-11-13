package tallyvane.platform.llm

/**
 * A [Model] that answers from a script, a spec at a time, and remembers what it was asked (ADR-044).
 *
 * A spec with nothing scripted, or with its script used up, is [ModelReply.Unavailable], so a test that
 * forgot to script a call fails on the outcome and not on a null.
 */
class ModelFake : Model {
    private val scripted = mutableMapOf<String, ArrayDeque<ModelReply>>()
    private val asked = mutableListOf<ModelCall>()

    /**
     * Every call made, in order.
     */
    val calls: List<ModelCall> get() = asked.toList()

    /**
     * The next call of [spec] is answered with [text].
     */
    fun answers(spec: String, text: String): ModelFake = replies(spec, ModelReply.Answered(text, COST, false))

    /**
     * The next call of [spec] ends as [reply].
     */
    fun replies(spec: String, reply: ModelReply): ModelFake {
        scripted.getOrPut(spec) { ArrayDeque() }.addLast(reply)
        return this
    }

    override suspend fun ask(call: ModelCall): ModelReply {
        asked += call
        return scripted[call.spec]?.removeFirstOrNull()
            ?: ModelReply.Unavailable("Nothing is scripted for ${call.spec}.")
    }

    companion object {
        val COST = TokenCount(100, 20, 0)
    }
}
