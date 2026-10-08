package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.NameRefused
import tallyvane.identity.contract.Registered
import tallyvane.identity.contract.Registrant
import tallyvane.platform.kernel.IdGeneratorFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val AT = Instant.parse("2026-10-02T09:00:00Z")
private val FIRST_ID = AccountId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

private fun registrant(subject: String = "google-1", name: String = "Ada Lovelace") =
    Registrant(subject, name, "ada@example.com", AT)

class AccountDirectorySpec :
    StringSpec(
        {
            "a new person gets an account, found afterwards by their Google subject" {
                val directory = AccountDirectory(KeptAccountsFake(), IdGeneratorFake())

                directory.register(registrant()) shouldBe Registered(FIRST_ID)

                directory.withGoogle("google-1") shouldBe FIRST_ID
            }

            "nobody is found for a subject nobody registered with" {
                AccountDirectory(KeptAccountsFake(), IdGeneratorFake()).withGoogle("google-1") shouldBe null
            }

            "registering the same person again finds the account made the first time" {
                val directory = AccountDirectory(KeptAccountsFake(), IdGeneratorFake())
                directory.register(registrant())

                directory.register(registrant(name = "Someone Else")) shouldBe Registered(FIRST_ID)
            }

            "a name identity does not accept creates nothing" {
                val kept = KeptAccountsFake()
                val directory = AccountDirectory(kept, IdGeneratorFake())

                directory.register(registrant(name = "   ")) shouldBe NameRefused()

                kept.withGoogle("google-1") shouldBe null
            }

            "a registration that loses a race to the same person's other one answers with the winner's account" {
                val winner = Uuid.parse("0199a000-0000-7000-8000-00000000000a")
                val kept = RacedAccounts(winner)

                AccountDirectory(kept, IdGeneratorFake()).register(registrant()) shouldBe Registered(AccountId(winner))
            }
        },
    )

/**
 * Accounts that look empty when asked first, then report that another registration of the same person
 * got there in between, the way a unique index answers a concurrent insert.
 */
private class RacedAccounts(private val winner: Uuid) : tallyvane.identity.application.port.KeptAccounts {
    private var asked = 0

    override fun withGoogle(subject: String): Uuid? = winner.takeIf { asked++ > 0 }

    override fun profileOf(id: Uuid): Profile? = null

    override fun add(account: tallyvane.identity.domain.Account): AccountAdded = AccountAdded.SubjectTaken

    override fun isAdministrator(id: Uuid): Boolean = false
}
