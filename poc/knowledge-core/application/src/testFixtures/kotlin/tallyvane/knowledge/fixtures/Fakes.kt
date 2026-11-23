package tallyvane.knowledge.fixtures

import tallyvane.knowledge.domain.Embedding
import tallyvane.knowledge.domain.port.CachedPrompt
import tallyvane.knowledge.domain.port.Diagnostics
import tallyvane.knowledge.domain.port.EmbeddingProvider
import tallyvane.knowledge.domain.port.LlmClient
import tallyvane.knowledge.domain.port.LlmReply
import tallyvane.knowledge.domain.port.LlmUsage
import tallyvane.knowledge.domain.port.Prompts

/**
 * A model that answers from a script: for each step, the replies it will give, in order. A step asked for
 * more often than it was scripted fails the test, so a pipeline that calls the model more than expected is seen.
 */
class ScriptedLlm(script: Map<String, List<String>>) : LlmClient {
    private val remaining: Map<String, ArrayDeque<String>> = script.mapValues { ArrayDeque(it.value) }
    private val seen = mutableListOf<CachedPrompt>()

    override suspend fun complete(prompt: CachedPrompt): LlmReply {
        seen += prompt
        val next = remaining[prompt.step]?.removeFirstOrNull() ?: error("No scripted reply left for the ${prompt.step} step.")
        return LlmReply(next, LlmUsage(inputTokens = 120, cacheCreationTokens = 0, cacheReadTokens = 0, outputTokens = 60), 1)
    }

    fun callsTo(step: String): Int = seen.count { it.step == step }

    fun promptsOf(step: String): List<CachedPrompt> = seen.filter { it.step == step }

    companion object {
        fun of(vararg script: Pair<String, List<String>>): ScriptedLlm = ScriptedLlm(script.toMap())
    }
}

/**
 * Embeddings from a table of concepts: a text gets the dimensions of every concept whose word it contains.
 * It makes the plumbing testable and deterministic; whether real embeddings put the right things near each
 * other is a question for the live test.
 */
class ConceptEmbeddings(private val concepts: Map<String, Map<Int, Float>>, private val dimensions: Int) : EmbeddingProvider {
    fun vectorOf(text: String): Embedding {
        val values = FloatArray(dimensions)
        val lower = text.lowercase()
        concepts.forEach { (word, weights) ->
            if (lower.contains(word)) weights.forEach { (dimension, weight) -> values[dimension] += weight }
        }
        return Embedding(values)
    }

    override suspend fun embed(texts: List<String>): List<Embedding> = texts.map(::vectorOf)
}

class FixedPrompts : Prompts {
    override fun prefix(step: String): String = "RULES FOR $step"
}

class RecordingDiagnostics : Diagnostics {
    private val dropped = mutableListOf<String>()
    private val unmapped = mutableListOf<String>()

    override fun dropped(entity: String, reason: String) {
        dropped += "$entity: $reason"
    }

    override fun unmapped(kind: String, value: String) {
        unmapped += "$kind: $value"
    }

    fun dropped(): List<String> = dropped.toList()

    fun unmapped(): List<String> = unmapped.toList()
}
