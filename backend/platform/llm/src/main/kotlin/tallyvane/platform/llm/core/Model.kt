package tallyvane.platform.llm.core

import tallyvane.platform.llm.ModelCall
import tallyvane.platform.llm.ModelReply

/**
 * The language model, asked one question at a time.
 *
 * Never called inside a transaction: a call takes seconds, and the decorators keep books in transactions of
 * their own.
 */
public interface Model {
    public suspend fun ask(call: ModelCall): ModelReply

    /**
     * The model of an installation that was given no key: it starts, and everything that needs no model works.
     */
    public class Absent : Model {
        override suspend fun ask(call: ModelCall): ModelReply = ModelReply.Unavailable("No model key is configured.")

        override fun toString(): String = "Model.Absent"
    }
}
