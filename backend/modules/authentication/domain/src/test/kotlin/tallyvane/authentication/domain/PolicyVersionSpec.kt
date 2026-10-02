package tallyvane.authentication.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import tallyvane.authentication.domain.FactorKind.Google
import tallyvane.authentication.domain.FactorKind.RecoveryCode
import tallyvane.authentication.domain.FactorKind.Totp
import tallyvane.authentication.domain.Step.Necessity.Always
import tallyvane.authentication.domain.Step.Necessity.WhenEnrolled
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val START = Instant.parse("2026-10-01T09:00:00Z")

private class VersionTranscript : PolicyVersion.Record {
    private val lines = mutableListOf<String>()

    override fun number(number: Int) {
        lines += "number $number"
    }

    override fun purpose(purpose: Purpose) {
        lines += "purpose $purpose"
    }

    override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) {
        lines += "step $accepts $necessity"
    }

    override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
        lines += "limits $attemptLifetime $maxFailures $firstDelay"
    }

    fun told(): List<String> = lines.toList()
}

private fun PolicyVersion.transcript(): List<String> = VersionTranscript().also { writeTo(it) }.told()

private fun versionOf(number: Int, purpose: Purpose, lifetime: Duration = 5.minutes) = PolicyDraft(
    purpose = purpose,
    steps = listOf(Step(setOf(Google), Always), Step(setOf(Totp, RecoveryCode), WhenEnrolled)),
    attemptLifetime = lifetime,
    maxFailures = 5,
    firstDelay = 1.seconds,
).check().reportTo(PassedPolicy).let { PolicyVersion(number, it) }

private fun PolicyVersion.Record.sayAll() {
    number(3)
    purpose(Purpose.Login)
    step(setOf(Google), Always)
    limits(5.minutes, 5, 1.seconds)
}

class PolicyVersionSpec :
    StringSpec(
        {
            "tells its number first, then the purpose, each step in order, then the limits" {
                versionOf(7, Purpose.Login).transcript() shouldBe listOf(
                    "number 7",
                    "purpose Login",
                    "step [Google] Always",
                    "step [Totp, RecoveryCode] WhenEnrolled",
                    "limits 5m 5 1s",
                )
            }

            "a version told back to restore tells the same story" {
                val original = versionOf(4, Purpose.StepUp, lifetime = 90.seconds)

                val restored = PolicyVersion.restore { record -> original.writeTo(record) }

                restored.transcript() shouldBe original.transcript()
            }

            "a restored version judges an attempt as the original did" {
                val original = versionOf(2, Purpose.Login)
                val restored = PolicyVersion.restore { record -> original.writeTo(record) }
                val attempt = Attempt(Purpose.Login, START).withVerified(
                    VerifiedFactor.identifying(
                        Google,
                        "google-subject-1",
                        START + 10.seconds,
                    ),
                )
                val enrollment = Enrollment(setOf(Totp))

                restored.progressOf(attempt, enrollment, now = START + 20.seconds) shouldBe
                    original.progressOf(attempt, enrollment, now = START + 20.seconds)
            }

            "a version is numbered from 1" {
                shouldThrow<IllegalArgumentException> { versionOf(0, Purpose.Login) }.message shouldContain "from 1"
            }

            "refuses a replay with no number" {
                shouldThrow<IllegalStateException> {
                    PolicyVersion.restore { record ->
                        record.purpose(Purpose.Login)
                        record.step(setOf(Google), Always)
                        record.limits(5.minutes, 5, 1.seconds)
                    }
                }.message shouldContain "no single number"
            }

            "refuses a replay with two numbers" {
                shouldThrow<IllegalStateException> {
                    PolicyVersion.restore { record ->
                        record.sayAll()
                        record.number(4)
                    }
                }.message shouldContain "no single number"
            }

            "refuses a replay with no purpose" {
                shouldThrow<IllegalStateException> {
                    PolicyVersion.restore { record ->
                        record.number(1)
                        record.step(setOf(Google), Always)
                        record.limits(5.minutes, 5, 1.seconds)
                    }
                }.message shouldContain "no single purpose"
            }

            "refuses a replay with no limits" {
                shouldThrow<IllegalStateException> {
                    PolicyVersion.restore { record ->
                        record.number(1)
                        record.purpose(Purpose.Login)
                        record.step(setOf(Google), Always)
                    }
                }.message shouldContain "no single set of limits"
            }

            "refuses, naming the number, a version that breaks the bounds the code sets today" {
                val refusal = shouldThrow<IllegalStateException> {
                    PolicyVersion.restore { record ->
                        record.number(9)
                        record.purpose(Purpose.Registration)
                        record.step(setOf(Google), Always)
                        record.limits(16.minutes, 5, 1.seconds)
                    }
                }.message

                refusal shouldContain "version 9"
                refusal shouldContain "AttemptLifetimeOutOfBounds"
                refusal shouldContain "Activate another version"
            }

            "refuses a version whose steps no longer keep the floor of its purpose" {
                shouldThrow<IllegalStateException> {
                    PolicyVersion.restore { record ->
                        record.number(1)
                        record.purpose(Purpose.Login)
                        record.step(setOf(Google), Always)
                        record.limits(5.minutes, 5, 1.seconds)
                    }
                }.message shouldContain "BelowTheFloor"
            }
        },
    )
