package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountDeleted
import tallyvane.platform.events.EventBus
import kotlin.time.Instant

class SessionsOfDeletedAccountsSpec :
    StringSpec(
        {
            "ends every session of an account that was deleted, and only theirs" {
                val harness = Harness()
                val first = harness.signedIn()
                val second = harness.signedIn()
                val strangers = harness.signedIn(account = Harness.OTHER_ACCOUNT)
                val bus = EventBus(listOf(SessionsOfDeletedAccounts(harness.sessions)))

                bus.publish(AccountDeleted(Harness.ACCOUNT, Instant.parse("2026-10-03T10:00:00Z")))

                harness.who(first) shouldBe "lapsed"
                harness.who(second) shouldBe "lapsed"
                harness.who(strangers) shouldBe "signed in ${Harness.OTHER_ACCOUNT.value}"
            }
        },
    )
