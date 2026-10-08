package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface
import tallyvane.sessions.domain.Lifetimes
import kotlin.time.Duration

class OpenSessionSpec :
    StringSpec(
        {
            "a completed sign-in is exchanged for a session that speaks for its person" {
                val harness = Harness()

                val session = harness.signedIn()

                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "the journal is told of the sign-in with the device the browser said it is" {
                val harness = Harness()

                harness.signedIn()

                val line = harness.journal.told().single()
                line.startsWith("signedIn ${Harness.ACCOUNT} ") shouldBe true
                line.endsWith("DeviceFacts(chrome on windows)") shouldBe true
            }

            "a sign-in that opens nothing tells the journal nothing" {
                val harness = Harness()

                harness.open.open(null, Harness.CHROME_ON_WINDOWS, Surface.App)
                harness.open.open(Secret("attempt-nobody-completed"), Harness.CHROME_ON_WINDOWS, Surface.App)

                harness.journal.told() shouldBe emptyList()
            }

            "the session's secret is a new one, never the sign-in's own" {
                val harness = Harness()
                val attempt = harness.finishedSigningIn()

                val session = harness.secretOf(harness.open.open(attempt, Harness.CHROME_ON_WINDOWS, Surface.App))

                session shouldNotBe attempt
                harness.who(attempt) shouldBe "lapsed"
            }

            "the browser is told the longest a session can ever last, so a loosened policy reaches its cookie" {
                val harness = Harness()
                val told = mutableListOf<Duration>()

                (
                    harness.open.open(
                        harness.finishedSigningIn(),
                        Harness.CHROME_ON_WINDOWS,
                        Surface.App,
                    ) as Opened.Issued
                    ).writeTo {
                        _,
                        lasting,
                    ->
                    told +=
                        lasting
                }

                told.single() shouldBe Lifetimes.LONGEST
            }

            "no cookie is nothing to open, and nothing is kept" {
                val harness = Harness()

                harness.open.open(null, Harness.CHROME_ON_WINDOWS, Surface.App) shouldBe Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 0
            }

            "a sign-in that is not complete is nothing to open, and nothing is kept" {
                val harness = Harness()

                harness.open.open(Secret("attempt-nobody-completed"), Harness.CHROME_ON_WINDOWS, Surface.App) shouldBe
                    Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 0
            }

            "a sign-in is exchanged once" {
                val harness = Harness()
                val attempt = harness.finishedSigningIn()
                harness.open.open(attempt, Harness.CHROME_ON_WINDOWS, Surface.App)

                harness.open.open(attempt, Harness.CHROME_ON_WINDOWS, Surface.App) shouldBe
                    Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 1
            }

            "an administrator's completed sign-in is exchanged for a session of the administrators' kind" {
                val harness = Harness()

                val session = harness.signedInAsAdmin()

                harness.who(session, Surface.Admin) shouldBe "signed in ${Harness.ACCOUNT.value}"
                harness.sessions.count() shouldBe 1
            }

            "a person who is not an administrator is refused a session on the administrators' door" {
                val harness = Harness()
                val attempt = harness.finishedSigningInAsAdmin()

                harness.open.open(attempt, Harness.CHROME_ON_WINDOWS, Surface.Admin) shouldBe
                    Opened.Failed.NotAnAdministrator()

                harness.sessions.count() shouldBe 0
                harness.journal.told() shouldBe emptyList()
            }

            "an ordinary sign-in is not taken on the administrators' door, nor an administrator's on the console's" {
                val harness = Harness()
                harness.admins.grant(Harness.ACCOUNT)
                val person = harness.finishedSigningIn()
                val admin = harness.finishedSigningInAsAdmin()

                harness.open.open(person, Harness.CHROME_ON_WINDOWS, Surface.Admin) shouldBe
                    Opened.Failed.NothingToOpen()
                harness.open.open(admin, Harness.CHROME_ON_WINDOWS, Surface.App) shouldBe
                    Opened.Failed.NothingToOpen()

                harness.sessions.count() shouldBe 0
            }

            "the journal is told of an administrator's sign-in like any other" {
                val harness = Harness()

                harness.signedInAsAdmin()

                harness.journal.told().single().startsWith("signedIn ${Harness.ACCOUNT} ") shouldBe true
            }
        },
    )
