package tallyvane.knowledge.infra.llm

import kotlinx.serialization.Serializable
import tallyvane.knowledge.domain.port.CachedPrompt
import tallyvane.knowledge.domain.port.LlmClient
import tallyvane.knowledge.domain.port.LlmReply
import java.time.Instant

/** One call to the model as the debug drawer shows it. Plain data on purpose: it is the shape of the response. */
@Serializable
class RecordedCall(
    val at: String,
    val step: String,
    val cachedPrefix: String,
    val dynamicSuffix: String,
    val reply: String?,
    val error: String?,
    val inputTokens: Int,
    val cacheCreationTokens: Int,
    val cacheReadTokens: Int,
    val outputTokens: Int,
    val latencyMillis: Long,
)

/**
 * Keeps the last calls to the model, with what was sent, what came back and what it cost, and passes every call on
 * to the client it wraps. Cost visibility is part of the point of the PoC, so a failed call is kept too.
 */
class RecordingLlmClient(private val delegate: LlmClient, private val capacity: Int = CAPACITY) : LlmClient {
    private val calls = ArrayDeque<RecordedCall>()

    override suspend fun complete(prompt: CachedPrompt): LlmReply {
        val started = System.nanoTime()
        try {
            val reply = delegate.complete(prompt)
            keep(
                RecordedCall(
                    Instant.now().toString(), prompt.step, prompt.cachedPrefix, prompt.dynamicSuffix, reply.text, null,
                    reply.usage.inputTokens, reply.usage.cacheCreationTokens, reply.usage.cacheReadTokens, reply.usage.outputTokens,
                    reply.latencyMillis,
                ),
            )
            return reply
        } catch (e: Exception) {
            keep(
                RecordedCall(
                    Instant.now().toString(), prompt.step, prompt.cachedPrefix, prompt.dynamicSuffix, null, e.message ?: e::class.simpleName,
                    0, 0, 0, 0, (System.nanoTime() - started) / NANOS_PER_MILLI,
                ),
            )
            throw e
        }
    }

    /** The recorded calls, the latest first. */
    fun recent(): List<RecordedCall> = synchronized(calls) { calls.toList().asReversed() }

    private fun keep(call: RecordedCall) {
        synchronized(calls) {
            calls.addLast(call)
            while (calls.size > capacity) calls.removeFirst()
        }
    }

    private companion object {
        const val CAPACITY = 200
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
