package tallyvane.platform.llm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.llm.core.Speaker
import tallyvane.platform.llm.core.Speakers
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val CHEAP = Uuid.parse("00000000-0000-7000-8000-0000000000e1")
private val CALL = ModelCall("extract_facts@1", ModelTier.Fast, "Read the words.", "{\"words\":\"hi\"}", ADA)

class RoutedModelSpec :
    StringSpec({
        class Heard : CallLog {
            val costs = mutableListOf<Pair<Double, Boolean>>()
            val models = mutableListOf<String>()

            override suspend fun record(
                at: Instant,
                account: Uuid,
                spec: String,
                provider: Uuid,
                model: String,
                tokens: TokenCount,
                cost: Double,
                remembered: Boolean,
            ) {
                costs += cost to remembered
                models += model
            }
        }

        class Answering :
            Speakers,
            Speaker {
            var asked = 0

            override fun of(id: Uuid, kind: ProviderKind, baseUrl: String?, key: Secret): Speaker = this

            override suspend fun ask(call: ModelCall, model: String): ModelReply {
                asked += 1
                return ModelReply.Answered("{}", TokenCount(1_000_000, 500_000, 0), remembered = false)
            }
        }

        fun provider(fast: String) = Provider(
            CHEAP,
            ProviderKind.OpenAiCompatible,
            "https://api.example.test/v1",
            Secret("sk-test"),
            fast,
            "big-model",
            0.5,
            2.0,
        )

        fun routed(active: Provider?, speaker: Answering, log: Heard, memo: Memo = MemoFake()) = RoutedModel(
            object : Providers {
                override suspend fun active(): Provider? = active
            },
            speaker,
            memo,
            log,
            ClockFake(Instant.parse("2026-11-13T10:00:00Z")),
        )

        "asks the active provider with the model of the tier, and records the price of the tokens" {
            val log = Heard()

            routed(provider("small-model"), Answering(), log).ask(CALL).shouldBeInstanceOf<ModelReply.Answered>()

            log.models shouldBe listOf("small-model")
            log.costs.single().first shouldBe (1.5 plusOrMinus 1e-9)
        }

        "answers the same question again from the memo, for nothing, and records that it did" {
            val log = Heard()
            val speaker = Answering()
            val model = routed(provider("small-model"), speaker, log)

            model.ask(CALL)
            model.ask(CALL)

            speaker.asked shouldBe 1
            log.costs.last() shouldBe (0.0 to true)
        }

        "asks again when the administrator switched to another model" {
            val memo = MemoFake()
            val speaker = Answering()

            routed(provider("small-model"), speaker, Heard(), memo).ask(CALL)
            routed(provider("other-model"), speaker, Heard(), memo).ask(CALL)

            speaker.asked shouldBe 2
        }

        "is unavailable while no provider is chosen" {
            routed(null, Answering(), Heard()).ask(CALL).shouldBeInstanceOf<ModelReply.Unavailable>()
        }
    })
