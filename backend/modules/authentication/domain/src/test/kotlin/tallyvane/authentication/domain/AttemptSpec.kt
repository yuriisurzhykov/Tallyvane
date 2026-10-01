package tallyvane.authentication.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tallyvane.authentication.domain.FactorKind.Google
import tallyvane.authentication.domain.FactorKind.Totp
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val START = Instant.parse("2026-10-01T09:00:00Z")

private fun at(secondsIn: Int) = START + secondsIn.seconds

/**
 * What an attempt said, one line per word, so a test can read the whole story at once.
 */
private class AttemptTranscript : Attempt.Record {
    private val lines = mutableListOf<String>()

    override fun started(purpose: Purpose, at: Instant) {
        lines += "started $purpose $at"
    }

    override fun verified(kind: FactorKind, at: Instant) {
        lines += "verified $kind $at"
    }

    override fun failed(at: Instant) {
        lines += "failed $at"
    }

    fun told(): List<String> = lines.toList()
}

private fun Attempt.transcript(): List<String> = AttemptTranscript().also { writeTo(it) }.told()

private fun restoring(replay: (Attempt.Record) -> Unit) = Attempt.restore(replay)

class AttemptSpec :
    StringSpec(
        {
            "tells its start, then each factor, then each wrong answer, in the order they came" {
                val attempt = Attempt(Purpose.AdminLogin, START)
                    .withVerified(VerifiedFactor(Google, at(10)))
                    .withFailure(at(20))
                    .withVerified(VerifiedFactor(Totp, at(40)))
                    .withFailure(at(30))

                attempt.transcript() shouldBe listOf(
                    "started AdminLogin $START",
                    "verified Google ${at(10)}",
                    "verified Totp ${at(40)}",
                    "failed ${at(20)}",
                    "failed ${at(30)}",
                )
            }

            "a fresh attempt tells only how it started" {
                Attempt(Purpose.Registration, START).transcript() shouldBe
                    listOf("started Registration $START")
            }

            "an attempt told back to restore tells the same story" {
                val original = Attempt(Purpose.StepUp, START)
                    .withVerified(VerifiedFactor(Google, at(10)))
                    .withFailure(at(20))
                    .withFailure(at(25))

                val restored = restoring { record -> original.writeTo(record) }

                restored.transcript() shouldBe original.transcript()
            }

            "a restored attempt is judged as the original was, including the pause its wrong answers earned" {
                val login = PassedPolicy.loginAfterGoogle()
                val original = Attempt(Purpose.Login, START)
                    .withVerified(VerifiedFactor(Google, at(10)))
                    .withFailure(at(20))
                    .withFailure(at(21))
                val restored = restoring { record -> original.writeTo(record) }
                val enrollment = Enrollment(setOf(Totp))

                restored.let { login.progressOf(it, enrollment, now = at(22)) } shouldBe
                    login.progressOf(original, enrollment, now = at(22))
            }

            "accepts factors and wrong answers at the very instant the attempt began, or at each other's" {
                val restored = restoring { record ->
                    record.started(Purpose.Login, START)
                    record.verified(Google, START)
                    record.verified(Totp, START)
                    record.failed(START)
                    record.failed(START)
                }

                restored.transcript().size shouldBe 5
            }

            "refuses a replay that says nothing" {
                val refusal = shouldThrow<IllegalStateException> { restoring { } }.message

                refusal shouldContain "never starts"
                refusal shouldContain "delete it"
            }

            "refuses a replay that starts twice" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.started(Purpose.Login, START)
                    }
                }.message shouldContain "starts twice"
            }

            "refuses a factor told before the start" {
                shouldThrow<IllegalStateException> {
                    restoring { record -> record.verified(Google, at(10)) }
                }.message shouldContain "before it starts"
            }

            "refuses a wrong answer told before the start" {
                shouldThrow<IllegalStateException> {
                    restoring { record -> record.failed(at(10)) }
                }.message shouldContain "before it starts"
            }

            "refuses a factor verified one second before the attempt began" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.verified(Google, START - 1.seconds)
                    }
                }.message shouldContain "verified before the attempt started"
            }

            "refuses a wrong answer given one second before the attempt began" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.failed(START - 1.seconds)
                    }
                }.message shouldContain "wrong answer comes before the attempt started"
            }

            "refuses factors told out of order" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.verified(Google, at(20))
                        record.verified(Totp, at(19))
                    }
                }.message shouldContain "before an earlier one"
            }

            "refuses wrong answers told out of order, which would move the pause that is owed" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.failed(at(20))
                        record.failed(at(19))
                    }
                }.message shouldContain "before an earlier one"
            }
        },
    )
