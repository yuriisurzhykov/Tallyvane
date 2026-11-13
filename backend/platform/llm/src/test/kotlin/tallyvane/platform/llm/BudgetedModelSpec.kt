package tallyvane.platform.llm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.platform.kernel.ClockFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-000000000001")
private val GRACE = Uuid.parse("00000000-0000-7000-8000-000000000002")
private val AT = Instant.parse("2026-11-13T09:00:00Z")

private fun call(account: Uuid = ADA): ModelCall =
    ModelCall("extract_facts@1", ModelTier.Fast, "Extract.", "words", account)

class BudgetedModelSpec :
    StringSpec(
        {
            "stops asking once the account has spent its day, and does not charge for the refusal" {
                val inner = ModelFake().answers("extract_facts@1", "a").answers("extract_facts@1", "b")
                val spending = SpendingFake()
                val model = BudgetedModel(inner, spending, ClockFake(AT), tokensADay = ModelFake.COST.total())

                model.ask(call()).shouldBeInstanceOf<ModelReply.Answered>()
                model.ask(call()).shouldBeInstanceOf<ModelReply.OverBudget>()

                inner.calls.size shouldBe 1
                spending.spentOn(ADA, "2026-11-13") shouldBe ModelFake.COST.total()
            }

            "keeps one account's spending away from another's" {
                val inner = ModelFake().answers("extract_facts@1", "a").answers("extract_facts@1", "b")
                val model = BudgetedModel(inner, SpendingFake(), ClockFake(AT), tokensADay = 1)

                model.ask(call(ADA)).shouldBeInstanceOf<ModelReply.Answered>()
                model.ask(call(GRACE)).shouldBeInstanceOf<ModelReply.Answered>()
            }

            "starts a new budget on a new day" {
                val inner = ModelFake().answers("extract_facts@1", "a").answers("extract_facts@1", "b")
                val spending = SpendingFake()

                BudgetedModel(inner, spending, ClockFake(AT), tokensADay = 1).ask(call())
                    .shouldBeInstanceOf<ModelReply.Answered>()
                BudgetedModel(inner, spending, ClockFake(AT + 1.days), tokensADay = 1).ask(call())
                    .shouldBeInstanceOf<ModelReply.Answered>()
            }

            "does not charge for a call that failed" {
                val spending = SpendingFake()
                val model = BudgetedModel(ModelFake(), spending, ClockFake(AT), tokensADay = 10)

                model.ask(call()).shouldBeInstanceOf<ModelReply.Unavailable>()

                spending.spentOn(ADA, "2026-11-13") shouldBe 0
            }
        },
    )
