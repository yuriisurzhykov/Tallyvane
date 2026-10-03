package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class OpenSessionSpec :
    StringSpec(
        {
            "a completed sign-in is exchanged for a session that speaks for its person" {
                val harness = Harness()

                val session = harness.signedIn()

                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "the session's secret is a new one, never the sign-in's own" {
                val harness = Harness()
                val attempt = harness.finishedSigningIn()

                val session = harness.secretOf(harness.open.open(attempt, Harness.CHROME_ON_WINDOWS))

                session shouldNotBe attempt
                harness.who(attempt) shouldBe "lapsed"
            }

            "the browser is told how long the session can last at most" {
                val harness = Harness()
                val told = mutableListOf<Duration>()

                (harness.open.open(harness.finishedSigningIn(), Harness.CHROME_ON_WINDOWS) as Opened.Issued).writeTo {
                        _,
                        lasting,
                    ->
                    told +=
                        lasting
                }

                told.single() shouldBe 7.days
            }

            "no cookie is nothing to open, and nothing is kept" {
                val harness = Harness()

                harness.open.open(null, Harness.CHROME_ON_WINDOWS) shouldBe Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 0
            }

            "a sign-in that is not complete is nothing to open, and nothing is kept" {
                val harness = Harness()

                harness.open.open(Secret("attempt-nobody-completed"), Harness.CHROME_ON_WINDOWS) shouldBe
                    Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 0
            }

            "a sign-in is exchanged once" {
                val harness = Harness()
                val attempt = harness.finishedSigningIn()
                harness.open.open(attempt, Harness.CHROME_ON_WINDOWS)

                harness.open.open(attempt, Harness.CHROME_ON_WINDOWS) shouldBe Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 1
            }
        },
    )
