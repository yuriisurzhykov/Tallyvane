package tallyvane.platform.llm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-000000000001")
private val NAMES = ModelNames("fast-model", "careful-model")

private fun call(input: String, spec: String = "extract_facts@1", tier: ModelTier = ModelTier.Fast): ModelCall =
    ModelCall(spec, tier, "Extract.", input, ADA)

class RememberingModelSpec :
    StringSpec(
        {
            "asks the model once for the same question, and says the second answer was remembered" {
                val inner = ModelFake().answers("extract_facts@1", """{"facts":[]}""")
                val model = RememberingModel(inner, MemoFake(), NAMES)

                val first = model.ask(call("words")).shouldBeInstanceOf<ModelReply.Answered>()
                val second = model.ask(call("words")).shouldBeInstanceOf<ModelReply.Answered>()

                inner.calls.size shouldBe 1
                first.remembered shouldBe false
                second.remembered shouldBe true
                second.text shouldBe """{"facts":[]}"""
                second.tokens shouldBe TokenCount(0, 0, 0)
            }

            "treats another input, another version of the spec and another tier as other questions" {
                val inner = ModelFake()
                    .answers("extract_facts@1", "a").answers("extract_facts@1", "b")
                    .answers("extract_facts@2", "c").answers("extract_facts@1", "d")
                val model = RememberingModel(inner, MemoFake(), NAMES)

                model.ask(call("words"))
                model.ask(call("other words"))
                model.ask(call("words", spec = "extract_facts@2"))
                model.ask(call("words", tier = ModelTier.Careful))

                inner.calls.size shouldBe 4
            }

            "does not remember a failure, so the next call asks again" {
                val inner = ModelFake()
                    .replies("extract_facts@1", ModelReply.Unavailable("down"))
                    .answers("extract_facts@1", "now")
                val memo = MemoFake()
                val model = RememberingModel(inner, memo, NAMES)

                model.ask(call("words")).shouldBeInstanceOf<ModelReply.Unavailable>()
                memo.size shouldBe 0
                model.ask(call("words")).shouldBeInstanceOf<ModelReply.Answered>().text shouldBe "now"
            }

            "refuses a spec that carries no version" {
                runCatching { call("words", spec = "extract_facts") }.isFailure shouldBe true
            }
        },
    )
