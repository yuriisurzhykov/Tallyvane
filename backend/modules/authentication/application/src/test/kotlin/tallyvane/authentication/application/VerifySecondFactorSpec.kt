package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redemption
import tallyvane.authentication.domain.Attempt
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun told(verification: Verification): String = when (verification) {
    is Verification.Verified -> verification.reportTo(
        object : Verification.Verified.Report<String> {
            override fun totp(): String = "verified by code"

            override fun recovery(remaining: Int): String = "verified by recovery code, $remaining left"
        },
    )

    is Verification.Failed -> {
        val waits = mutableListOf<Duration>()
        verification.retryAfter { waits += it }
        verification.toString().substringBefore("(") + waits.joinToString("") { " ${it.inWholeSeconds}s" }
    }
}

private fun redeemed(redemption: Redemption): String = redemption.reportTo(
    object : Redemption.Report<String> {
        override fun redeemed(account: AccountId, proofs: Set<Proof>, authenticatedAt: Instant): String =
            "redeemed ${proofs.sortedBy { it.name }}"

        override fun nothingToRedeem(): String = "nothing"
    },
)

private suspend fun Harness.code(attempt: Secret, code: String): String =
    told(verify.verify(attempt, Submission.TotpCode(code)))

private suspend fun Harness.recovery(attempt: Secret, code: String): String =
    told(verify.verify(attempt, Submission.RecoveryCode(code)))

/**
 * Attempts that never accept a save: another request got there first.
 */
private class AlwaysSuperseded(private val real: Attempts) : Attempts by real {
    override fun save(key: Digest, attempt: Attempt): AttemptSaveOutcome = AttemptSaveOutcome.Superseded
}

private suspend fun withTotp(body: suspend (Harness, Harness.TotpSetUp) -> Unit) {
    val harness = Harness()
    harness.accounts.knows("sub-1")
    body(harness, harness.enableTotp("sub-1"))
}

class VerifySecondFactorSpec :
    StringSpec(
        {
            "an account without TOTP is complete after Google, and a code has nowhere to go" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val attempt = harness.signedInWithGoogle("sub-1")

                harness.standing(attempt) shouldBe "complete [Google]"
                harness.code(attempt, "123456") shouldBe "NotWanted"
            }

            "with TOTP on, Google is not enough: the attempt waits for a code or a recovery code" {
                withTotp { harness, _ ->
                    val attempt = harness.signedInWithGoogle("sub-1")

                    harness.standing(attempt) shouldBe "awaiting [RecoveryCode, Totp]"
                    redeemed(harness.redemptions.redeem(attempt)) shouldBe "nothing"
                }
            }

            "a right code completes the sign-in, which can then be redeemed with both proofs" {
                withTotp { harness, setup ->
                    val attempt = harness.signedInWithGoogle("sub-1")

                    harness.code(attempt, setup.app.codeAt(harness.clock.now())) shouldBe "verified by code"

                    harness.standing(attempt) shouldBe "complete [Google, Totp]"
                    redeemed(harness.redemptions.redeem(attempt)) shouldBe "redeemed [Google, Totp]"
                }
            }

            "a code cannot be answered before Google has been" {
                withTotp { harness, setup ->
                    val pressed = harness.pressSignIn()

                    harness.code(pressed.attempt, setup.app.codeAt(harness.clock.now())) shouldBe "Closed"
                }
            }

            "a wrong code is refused with the pause it earns, and the refusal still commits" {
                withTotp { harness, _ ->
                    val attempt = harness.signedInWithGoogle("sub-1")

                    harness.code(attempt, "000000") shouldBe "WrongCode 1s"

                    harness.endings().last() shouldBe TransactionRunnerFake.Ending.Committed
                }
            }

            "a code typed during the pause is not even checked, and the pause ends" {
                withTotp { harness, setup ->
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.code(attempt, "000000")

                    harness.code(attempt, setup.app.codeAt(harness.clock.now())) shouldBe "Paused 1s"
                    harness.standing(attempt) shouldBe "paused until 2026-10-02T09:00:32Z"

                    harness.clock.passes(1.seconds)
                    harness.code(attempt, setup.app.codeAt(harness.clock.now())) shouldBe "verified by code"
                }
            }

            "five wrong codes end the attempt" {
                withTotp { harness, _ ->
                    val attempt = harness.signedInWithGoogle("sub-1")
                    val answers = (1..5).map {
                        harness.clock.passes(20.seconds)
                        harness.code(attempt, "000000")
                    }

                    answers.take(4).all { it.startsWith("WrongCode") } shouldBe true
                    answers.last() shouldBe "Closed"
                    harness.standing(attempt) shouldBe "exhausted"
                    harness.code(attempt, "000000") shouldBe "Closed"
                }
            }

            "the account remembers wrong codes across attempts, so a new attempt starts with its pause" {
                withTotp { harness, setup ->
                    val first = harness.signedInWithGoogle("sub-1")
                    repeat(3) {
                        harness.code(first, "000000")
                        harness.clock.passes(10.seconds)
                    }
                    harness.clock.passes(1.seconds)
                    harness.code(first, "000000")
                    val second = harness.signedInWithGoogle("sub-1")

                    harness.code(second, setup.app.codeAt(harness.clock.now())) shouldBe "Paused 8s"

                    harness.clock.passes(8.seconds)
                    harness.code(second, setup.app.codeAt(harness.clock.now())) shouldBe "verified by code"
                }
            }

            "a code that was accepted once is not accepted again, in another attempt either" {
                withTotp { harness, setup ->
                    val first = harness.signedInWithGoogle("sub-1")
                    val second = harness.signedInWithGoogle("sub-1")
                    val code = setup.app.codeAt(harness.clock.now())

                    harness.code(first, code) shouldBe "verified by code"
                    harness.code(second, code) shouldBe "WrongCode 1s"
                }
            }

            "a recovery code completes the sign-in, is spent, and retires the seed" {
                withTotp { harness, setup ->
                    val attempt = harness.signedInWithGoogle("sub-1")

                    harness.recovery(attempt, setup.recoveryCodes.first()) shouldBe
                        "verified by recovery code, 9 left"

                    redeemed(harness.redemptions.redeem(attempt)) shouldBe "redeemed [Google, RecoveryCode]"
                    val later = harness.signedInWithGoogle("sub-1")
                    harness.standing(later) shouldBe "awaiting [RecoveryCode]"
                    harness.code(later, setup.app.codeAt(harness.clock.now())) shouldBe "NotWanted"
                    harness.standing(later) shouldBe "awaiting [RecoveryCode]"
                }
            }

            "a recovery code is typed without regard to case, dashes or spaces, and works once" {
                withTotp { harness, setup ->
                    val first = harness.signedInWithGoogle("sub-1")
                    val typed = setup.recoveryCodes[2].lowercase().replace("-", " ")

                    harness.recovery(first, typed) shouldBe "verified by recovery code, 9 left"

                    val second = harness.signedInWithGoogle("sub-1")
                    harness.recovery(second, setup.recoveryCodes[2]) shouldBe "WrongCode 1s"
                }
            }

            "a wrong recovery code counts against the attempt and not against the account" {
                withTotp { harness, setup ->
                    val first = harness.signedInWithGoogle("sub-1")
                    harness.recovery(first, "NOTACODE-1234") shouldBe "WrongCode 1s"

                    val second = harness.signedInWithGoogle("sub-1")

                    harness.code(second, setup.app.codeAt(harness.clock.now())) shouldBe "verified by code"
                }
            }

            "the last recovery code of a person whose seed was retired leaves Google alone enough" {
                withTotp { harness, setup ->
                    setup.recoveryCodes.forEach { code ->
                        val attempt = harness.signedInWithGoogle("sub-1")
                        harness.recovery(attempt, code)
                    }

                    harness.standing(harness.signedInWithGoogle("sub-1")) shouldBe "complete [Google]"
                }
            }

            "a confirmation of a dangerous act is answered the same way" {
                withTotp { harness, setup ->
                    val pressed = harness.pressStepUp()
                    harness.returnWith(
                        pressed,
                        GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com")),
                    )

                    harness.standing(pressed.attempt) shouldBe "awaiting [RecoveryCode, Totp]"
                    harness.code(pressed.attempt, setup.app.codeAt(harness.clock.now())) shouldBe "verified by code"
                    redeemed(harness.redemptions.redeemStepUp(pressed.attempt)) shouldBe "redeemed [Google, Totp]"
                }
            }

            "nothing is answered without a cookie, under a secret nobody holds, or in a registration" {
                withTotp { harness, _ ->
                    harness.verify.verify(null, Submission.TotpCode("123456")).let(::told) shouldBe "Closed"
                    harness.code(Secret("nobody"), "123456") shouldBe "Closed"
                    val registering = harness.line(
                        harness.returnWith(
                            harness.pressSignIn(),
                            GoogleAnswer.Vouched("sub-new", GoogleProfile("Bob", "bob@example.com")),
                        ),
                    ).substringAfter("registering ")
                    harness.code(Secret(registering), "123456") shouldBe "Closed"
                }
            }

            "an attempt that outlived its lifetime is closed" {
                withTotp { harness, setup ->
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.clock.passes(6.minutes)

                    harness.code(attempt, setup.app.codeAt(harness.clock.now())) shouldBe "Closed"
                }
            }

            "a request that lost the race to change the attempt is told to ask again and proves nothing" {
                withTotp { harness, setup ->
                    val attempt = harness.signedInWithGoogle("sub-1")
                    val losing = harness.verifyOver(AlwaysSuperseded(harness.store))

                    told(losing.verify(attempt, Submission.TotpCode(setup.app.codeAt(harness.clock.now())))) shouldBe
                        "Busy"

                    harness.standing(attempt) shouldBe "awaiting [RecoveryCode, Totp]"
                }
            }
        },
    )
