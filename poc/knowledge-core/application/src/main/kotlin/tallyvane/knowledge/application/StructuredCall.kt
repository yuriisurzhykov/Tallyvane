package tallyvane.knowledge.application

import tallyvane.knowledge.domain.port.CachedPrompt
import tallyvane.knowledge.domain.port.LlmClient
import tallyvane.knowledge.domain.port.Prompts

/**
 * One call to the model whose reply must be understood and accepted: it is parsed by [parse], which throws
 * [InvalidLlmOutput] with the reason when it cannot accept it. The reason is given back to the model once;
 * a second rejection fails the step in the open, with that reason.
 */
class StructuredCall(private val llm: LlmClient, private val prompts: Prompts) {
    internal suspend fun <T> run(step: String, dynamicSuffix: String, prefixExtra: String = "", parse: (String) -> T): T {
        val prefix = prompts.prefix(step) + prefixExtra
        var suffix = dynamicSuffix
        var problem = "no reply"
        repeat(ATTEMPTS) {
            val reply = llm.complete(CachedPrompt(step, prefix, suffix))
            try {
                return parse(reply.text)
            } catch (e: InvalidLlmOutput) {
                problem = e.message ?: "the reply was not accepted"
                suffix = "$dynamicSuffix\n\nYour previous reply was rejected: $problem\nReply again with the corrected JSON only."
            }
        }
        throw LlmStepFailed(step, problem)
    }

    private companion object {
        const val ATTEMPTS = 2
    }
}
