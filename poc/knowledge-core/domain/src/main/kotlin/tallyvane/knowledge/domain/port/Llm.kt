package tallyvane.knowledge.domain.port

import tallyvane.knowledge.domain.Embedding

/**
 * A call to the language model: a [cachedPrefix] that is the same on every call of its [step] (the rules,
 * the schema, the examples), then a [dynamicSuffix] that is not. The split is what lets the prefix be cached.
 */
class CachedPrompt(
    val step: String,
    val cachedPrefix: String,
    val dynamicSuffix: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    private companion object {
        const val DEFAULT_MAX_TOKENS = 2048
    }
}

/** What a call cost, in the four counts the model reports. */
class LlmUsage(
    val inputTokens: Int,
    val cacheCreationTokens: Int,
    val cacheReadTokens: Int,
    val outputTokens: Int,
)

class LlmReply(val text: String, val usage: LlmUsage, val latencyMillis: Long)

interface LlmClient {
    suspend fun complete(prompt: CachedPrompt): LlmReply
}

interface EmbeddingProvider {
    suspend fun embed(texts: List<String>): List<Embedding>
}

/** The editable prompt for a step: its rules, its output schema and its examples. */
interface Prompts {
    fun prefix(step: String): String
}

/** Where things that are dropped or not understood are said, so they are not lost silently. */
interface Diagnostics {
    fun dropped(entity: String, reason: String)

    fun unmapped(kind: String, value: String)
}
