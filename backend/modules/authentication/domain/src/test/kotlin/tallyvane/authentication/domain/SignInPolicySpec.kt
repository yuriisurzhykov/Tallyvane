package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.domain.FactorKind.Google
import tallyvane.authentication.domain.FactorKind.RecoveryCode
import tallyvane.authentication.domain.FactorKind.Totp
import tallyvane.authentication.domain.Step.Necessity.Always
import tallyvane.authentication.domain.Step.Necessity.WhenEnrolled
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val START = Instant.parse("2026-10-01T09:00:00Z")

private val GOOGLE = Step(setOf(Google), Always)
private val SECOND_FACTOR_IF_ENABLED = Step(setOf(Totp, RecoveryCode), WhenEnrolled)
private val SECOND_FACTOR_ALWAYS = Step(setOf(Totp, RecoveryCode), Always)

private val SECOND_FACTOR = setOf(Totp, RecoveryCode)

private val NOTHING_SET_UP = Enrollment(emptySet())
private val TOTP_SET_UP = Enrollment(setOf(Totp))

private fun policyOf(purpose: Purpose, vararg steps: Step) =
    PolicyDraft(purpose, steps.toList(), attemptLifetime = 5.minutes, maxFailures = 5, firstDelay = 1.seconds)
        .check()
        .reportTo(PassedPolicy)

private val REGISTRATION = policyOf(Purpose.Registration, GOOGLE)
private val LOGIN = policyOf(Purpose.Login, GOOGLE, SECOND_FACTOR_IF_ENABLED)
private val ADMIN_LOGIN = policyOf(Purpose.AdminLogin, GOOGLE, SECOND_FACTOR_ALWAYS)
private val STEP_UP = policyOf(Purpose.StepUp, GOOGLE, SECOND_FACTOR_IF_ENABLED)

private fun Attempt.verifying(kind: FactorKind, secondsIn: Int) =
    withVerified(VerifiedFactor(kind, START + secondsIn.seconds))

private fun Attempt.failingTimes(count: Int, secondsIn: Int) =
    (1..count).fold(this) { attempt, _ -> attempt.withFailure(START + secondsIn.seconds) }

class SignInPolicySpec :
    StringSpec(
        {
            "registration is complete after Google alone" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                REGISTRATION.progressOf(attempt, NOTHING_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Complete(setOf(Google), START + 10.seconds)
            }

            "a fresh attempt asks for Google first, before anything about the account is known" {
                LOGIN.progressOf(Attempt(START), Enrollment.Unknown, now = START) shouldBe
                    Progress.Awaiting(setOf(Google))
            }

            "an account without TOTP signs in with Google alone" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                LOGIN.progressOf(attempt, NOTHING_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Complete(setOf(Google), START + 10.seconds)
            }

            // The user raised their own bar: the policy did not change, their enrolled factors did.
            "an account with TOTP is asked for the second factor after Google" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Awaiting(SECOND_FACTOR)
            }

            "TOTP completes it, and the session remembers both factors and the later time" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10).verifying(Totp, secondsIn = 30)

                LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 31.seconds) shouldBe
                    Progress.Complete(setOf(Google, Totp), START + 30.seconds)
            }

            "a recovery code satisfies the same step as TOTP" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10).verifying(RecoveryCode, secondsIn = 30)

                LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 31.seconds) shouldBe
                    Progress.Complete(setOf(Google, RecoveryCode), START + 30.seconds)
            }

            // Without this case the policy would either lock the administrator out or let them in
            // unrestricted; both are wrong (ADR-078).
            "an administrator without TOTP is signed in restricted to setting it up" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                ADMIN_LOGIN.progressOf(attempt, NOTHING_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Restricted(setOf(Google), START + 10.seconds, toSetUp = SECOND_FACTOR)
            }

            "an administrator with TOTP is asked for it like anyone else" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                ADMIN_LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Awaiting(SECOND_FACTOR)
            }

            "the attempt expires exactly at the end of its lifetime, not a moment later" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 5.minutes - 1.seconds) shouldBe
                    Progress.Awaiting(SECOND_FACTOR)
                LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 5.minutes) shouldBe
                    Progress.Expired()
            }

            // An expired attempt must not complete even if its factors are all there: the
            // lifetime is checked before anything else.
            "an expired attempt is expired even when every factor was verified" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)

                REGISTRATION.progressOf(attempt, NOTHING_SET_UP, now = START + 5.minutes) shouldBe Progress.Expired()
            }

            "the fifth wrong answer ends the attempt" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)
                val enrollment = TOTP_SET_UP
                val later = START + 60.seconds

                LOGIN.progressOf(attempt.failingTimes(4, secondsIn = 20), enrollment, later) shouldBe
                    Progress.Awaiting(SECOND_FACTOR)
                LOGIN.progressOf(attempt.failingTimes(5, secondsIn = 20), enrollment, later) shouldBe
                    Progress.Exhausted()
            }

            "each wrong answer doubles the pause: 1, 2, 4, 8 seconds" {
                val google = Attempt(START).verifying(Google, secondsIn = 10)

                listOf(1 to 1, 2 to 2, 3 to 4, 4 to 8).forEach { (failures, pause) ->
                    val attempt = google.failingTimes(failures, secondsIn = 20)

                    LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 20.seconds) shouldBe
                        Progress.Paused(SECOND_FACTOR, until = START + 20.seconds + pause.seconds)
                    LOGIN.progressOf(attempt, TOTP_SET_UP, now = START + 20.seconds + pause.seconds) shouldBe
                        Progress.Awaiting(SECOND_FACTOR)
                }
            }

            // Otherwise whoever took over the Google account could switch TOTP off with Google alone.
            "confirming a dangerous action with TOTP on takes Google and the code, not Google alone" {
                val google = Attempt(START).verifying(Google, secondsIn = 10)

                STEP_UP.progressOf(google, TOTP_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Awaiting(SECOND_FACTOR)
                STEP_UP.progressOf(
                    google.verifying(Totp, secondsIn = 20),
                    TOTP_SET_UP,
                    now = START + 21.seconds,
                ) shouldBe
                    Progress.Complete(setOf(Google, Totp), START + 20.seconds)
            }

            "confirming a dangerous action without TOTP takes Google alone" {
                val google = Attempt(START).verifying(Google, secondsIn = 10)

                STEP_UP.progressOf(google, NOTHING_SET_UP, now = START + 11.seconds) shouldBe
                    Progress.Complete(setOf(Google), START + 10.seconds)
            }

            // Tightening applies mid-sign-in (ADR-078): the attempt stores what happened, never
            // which policy it started under.
            "the same attempt is judged by whichever policy is active when asked" {
                val attempt = Attempt(START).verifying(Google, secondsIn = 10)
                val enrollment = TOTP_SET_UP

                REGISTRATION.progressOf(attempt, enrollment, START + 11.seconds) shouldBe
                    Progress.Complete(setOf(Google), START + 10.seconds)
                ADMIN_LOGIN.progressOf(attempt, enrollment, START + 11.seconds) shouldBe
                    Progress.Awaiting(SECOND_FACTOR)
            }
        },
    )
