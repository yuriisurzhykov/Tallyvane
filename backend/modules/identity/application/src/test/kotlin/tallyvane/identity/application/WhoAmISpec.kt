package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Registered
import tallyvane.identity.contract.Registrant
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val FIRST = AccountId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
private val AT = Instant.parse("2026-10-02T09:00:00Z")

private fun told(known: WhoAmIOutcome): String {
    val lines = mutableListOf<String>()
    (known as WhoAmIOutcome.Known).writeTo { id, name -> lines += "$id $name" }
    return lines.single()
}

class WhoAmISpec :
    StringSpec(
        {
            "tells a person the account they hold and what they are called" {
                val kept = KeptAccountsFake()
                AccountDirectory(kept, IdGeneratorFake())
                    .register(Registrant("google-1", "Ada Lovelace", "ada@example.com", AT)) shouldBe Registered(FIRST)
                val id = FIRST
                val whoAmI = WhoAmIUseCase.WhoAmI(kept, TransactionRunnerFake())

                told(whoAmI.whoIs(id)) shouldBe "${id.value} Ada Lovelace"
            }

            "says the account is gone when nobody is kept under it" {
                val whoAmI = WhoAmIUseCase.WhoAmI(KeptAccountsFake(), TransactionRunnerFake())

                whoAmI.whoIs(AccountId(Uuid.parse("0199a000-0000-7000-8000-0000000000ff"))) shouldBe
                    WhoAmIOutcome.Failed.Gone()
            }
        },
    )
