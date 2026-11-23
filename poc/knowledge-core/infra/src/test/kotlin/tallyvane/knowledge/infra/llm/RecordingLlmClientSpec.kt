package tallyvane.knowledge.infra.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import tallyvane.knowledge.domain.port.CachedPrompt
import tallyvane.knowledge.domain.port.LlmClient
import tallyvane.knowledge.domain.port.LlmReply
import tallyvane.knowledge.domain.port.LlmUsage

private class Echo : LlmClient {
    override suspend fun complete(prompt: CachedPrompt): LlmReply {
        if (prompt.dynamicSuffix == "fail") throw LlmCallFailed("down")
        return LlmReply("reply to ${prompt.dynamicSuffix}", LlmUsage(10, 20, 30, 5), 7)
    }
}

class RecordingLlmClientSpec :
    StringSpec(
        {
            "a call is passed on unchanged and kept with what it cost" {
                runBlocking {
                    val client = RecordingLlmClient(Echo())
                    client.complete(CachedPrompt("extract", "prefix", "one")).text shouldBe "reply to one"
                    val call = client.recent().single()
                    call.step shouldBe "extract"
                    call.cachedPrefix shouldBe "prefix"
                    call.dynamicSuffix shouldBe "one"
                    call.reply shouldBe "reply to one"
                    call.inputTokens shouldBe 10
                    call.cacheCreationTokens shouldBe 20
                    call.cacheReadTokens shouldBe 30
                    call.outputTokens shouldBe 5
                    call.latencyMillis shouldBe 7
                }
            }

            "a failed call is kept too, and the failure goes on" {
                runBlocking {
                    val client = RecordingLlmClient(Echo())
                    shouldThrow<LlmCallFailed> { client.complete(CachedPrompt("tags", "p", "fail")) }
                    val call = client.recent().single()
                    call.reply shouldBe null
                    call.error shouldBe "down"
                }
            }

            "only the latest calls are kept, the newest first" {
                runBlocking {
                    val client = RecordingLlmClient(Echo(), capacity = 3)
                    (1..5).forEach { client.complete(CachedPrompt("extract", "p", "call $it")) }
                    client.recent().map { it.dynamicSuffix } shouldBe listOf("call 5", "call 4", "call 3")
                }
            }
        },
    )
