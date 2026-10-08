package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.contract.Proof
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface
import tallyvane.sessions.domain.Freshness
import kotlin.time.Duration.Companion.minutes

class ConfirmStepUpSpec :
    StringSpec(
        {
            "a session is fresh when it begins: a person who has just signed in is asked for nothing more" {
                val harness = Harness()

                harness.freshnessOf(harness.signedIn()) shouldBe Freshness.Fresh
            }

            "a session goes stale once the freshness the lifetimes allow has passed" {
                val harness = Harness()
                val session = harness.signedIn()

                harness.clock.advance(5.minutes)

                harness.freshnessOf(session) shouldBe Freshness.Stale
            }

            "confirming makes a stale session fresh again, and it stays the same session" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(6.minutes)
                val before = harness.shown(session).single().id

                val confirmation = harness.finishedConfirming()
                harness.confirmStepUp.confirm(session, confirmation, Surface.App) shouldBe Confirmed.Done()

                harness.freshnessOf(session) shouldBe Freshness.Fresh
                harness.shown(session).single().id shouldBe before
            }

            "the freshness window is the one in force, a policy loosened reaches a session already begun" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(6.minutes)

                harness.lifetimeVersions.activate(
                    idle = 1.minutes * 60 * 24,
                    absolute = 1.minutes * 60 * 24 * 7,
                    freshness = 10.minutes,
                )

                harness.freshnessOf(session) shouldBe Freshness.Fresh
            }

            "confirming does not make a session live longer" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.lifetimeVersions.activate(idle = 25.minutes, absolute = 30.minutes)
                harness.clock.advance(20.minutes)
                harness.confirmStepUp.confirm(session, harness.finishedConfirming(), Surface.App)

                harness.clock.advance(11.minutes)

                harness.who(session) shouldBe "lapsed"
            }

            "a confirmation is taken once" {
                val harness = Harness()
                val session = harness.signedIn()
                val confirmation = harness.finishedConfirming()
                harness.confirmStepUp.confirm(session, confirmation, Surface.App)

                harness.confirmStepUp.confirm(session, confirmation, Surface.App) shouldBe
                    Confirmed.Failed.NothingToConfirm()
            }

            "a sign-in is not a confirmation, and nothing changes" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(6.minutes)
                val signIn = harness.finishedSigningIn(attempt = Secret("a-sign-in"))

                harness.confirmStepUp.confirm(session, signIn, Surface.App) shouldBe Confirmed.Failed.NothingToConfirm()

                harness.freshnessOf(session) shouldBe Freshness.Stale
            }

            "no cookie is nothing to confirm" {
                val harness = Harness()

                harness.confirmStepUp.confirm(harness.signedIn(), null, Surface.App) shouldBe
                    Confirmed.Failed.NothingToConfirm()
            }

            "another person's confirmation changes nothing and is spent" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(6.minutes)
                val strangers = harness.finishedConfirming(account = Harness.OTHER_ACCOUNT)

                harness.confirmStepUp.confirm(session, strangers, Surface.App) shouldBe Confirmed.Failed.WrongAccount()

                harness.freshnessOf(session) shouldBe Freshness.Stale
                harness.confirmStepUp.confirm(session, strangers, Surface.App) shouldBe
                    Confirmed.Failed.NothingToConfirm()
            }

            "refuses a request with no session or an ended one" {
                val harness = Harness()
                val confirmation = harness.finishedConfirming()

                harness.confirmStepUp.confirm(null, confirmation, Surface.App) shouldBe
                    Confirmed.Failed.SignInRequired()
                harness.confirmStepUp.confirm(Secret("never-issued"), confirmation, Surface.App) shouldBe
                    Confirmed.Failed.SessionExpired()
            }

            "a proof by another factor joins how the person proved who they are" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(6.minutes)

                harness.confirmStepUp.confirm(
                    session,
                    harness.finishedConfirming(proofs = setOf(Proof.Google, Proof.Totp)),
                    Surface.App,
                )

                harness.freshnessOf(session) shouldBe Freshness.Fresh
            }

            "a session of the other door confirms nothing, and the confirmation is not spent" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(6.minutes)
                val confirmation = harness.finishedConfirming()

                harness.confirmStepUp.confirm(session, confirmation, Surface.Admin) shouldBe
                    Confirmed.Failed.SessionExpired()

                harness.confirmStepUp.confirm(session, confirmation, Surface.App) shouldBe Confirmed.Done()
            }
        },
    )
