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

    override fun identified(kind: FactorKind, subject: String, at: Instant) {
        lines += "identified $kind $subject $at"
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

private const val SUBJECT = "google-subject-1"

private fun google(at: Instant, subject: String = SUBJECT) = VerifiedFactor.identifying(Google, subject, at)

private fun totp(at: Instant) = VerifiedFactor.confirming(Totp, at)

/**
 * Reads only the moment a paused attempt resumes; any other case fails the test.
 */
private object Paused : Progress.Report<Instant> {
    override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String) = unexpected()

    override fun restricted(
        factors: Set<FactorKind>,
        authenticatedAt: Instant,
        subject: String,
        toSetUp: Set<FactorKind>,
    ) = unexpected()

    override fun awaiting(accepted: Set<FactorKind>) = unexpected()

    override fun paused(accepted: Set<FactorKind>, until: Instant) = until

    override fun exhausted() = unexpected()

    override fun expired() = unexpected()

    private fun unexpected(): Nothing = throw AssertionError("Expected a paused attempt")
}

/**
 * Reads only whose account a complete attempt proved; any other case fails the test.
 */
private object Whose : Progress.Report<String> {
    override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String) = subject

    override fun restricted(
        factors: Set<FactorKind>,
        authenticatedAt: Instant,
        subject: String,
        toSetUp: Set<FactorKind>,
    ) = unexpected()

    override fun awaiting(accepted: Set<FactorKind>) = unexpected()

    override fun paused(accepted: Set<FactorKind>, until: Instant) = unexpected()

    override fun exhausted() = unexpected()

    override fun expired() = unexpected()

    private fun unexpected(): Nothing = throw AssertionError("Expected a complete attempt")
}

private fun restoring(replay: (Attempt.Record) -> Unit) = Attempt.restore(replay)

class AttemptSpec :
    StringSpec(
        {
            "tells its start, then each factor, then each wrong answer, in the order they came" {
                val attempt = Attempt(Purpose.AdminLogin, START)
                    .withVerified(google(at(10)))
                    .withFailure(at(20))
                    .withVerified(totp(at(40)))
                    .withFailure(at(30))

                attempt.transcript() shouldBe listOf(
                    "started AdminLogin $START",
                    "identified Google $SUBJECT ${at(10)}",
                    "verified Totp ${at(40)}",
                    "failed ${at(20)}",
                    "failed ${at(30)}",
                )
            }

            "an attempt says which purpose it was started for, and no other" {
                val registration = Attempt(Purpose.Registration, START)

                registration.isFor(Purpose.Registration) shouldBe true
                registration.isFor(Purpose.Login) shouldBe false
            }

            "a fresh attempt tells only how it started" {
                Attempt(Purpose.Registration, START).transcript() shouldBe
                    listOf("started Registration $START")
            }

            "an attempt told back to restore tells the same story" {
                val original = Attempt(Purpose.StepUp, START)
                    .withVerified(google(at(10)))
                    .withFailure(at(20))
                    .withFailure(at(25))

                val restored = restoring { record -> original.writeTo(record) }

                restored.transcript() shouldBe original.transcript()
            }

            "a restored attempt is judged as the original was, including the pause its wrong answers earned" {
                val login = PassedPolicy.loginAfterGoogle()
                val original = Attempt(Purpose.Login, START)
                    .withVerified(google(at(10)))
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
                    record.identified(Google, SUBJECT, START)
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
                    restoring { record -> record.identified(Google, SUBJECT, at(10)) }
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
                        record.identified(Google, SUBJECT, START - 1.seconds)
                    }
                }.message shouldContain "out of order"
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
                        record.identified(Google, SUBJECT, at(20))
                        record.verified(Totp, at(19))
                    }
                }.message shouldContain "out of order"
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

            "a wrong answer stamped before an earlier one is recorded at the earlier one's moment" {
                val attempt = Attempt(Purpose.Login, START).withFailure(at(20)).withFailure(at(19))

                attempt.transcript() shouldBe listOf("started Login $START", "failed ${at(20)}", "failed ${at(20)}")
            }

            "a wrong answer stamped before the start is recorded at the start" {
                Attempt(Purpose.Login, START).withFailure(START - 1.seconds).transcript() shouldBe
                    listOf("started Login $START", "failed $START")
            }

            "a factor stamped before an earlier one is recorded at the earlier one's moment" {
                val attempt = Attempt(Purpose.Login, START).withVerified(google(at(20))).withVerified(totp(at(19)))

                attempt.transcript() shouldBe listOf(
                    "started Login $START",
                    "identified Google $SUBJECT ${at(20)}",
                    "verified Totp ${at(20)}",
                )
            }

            "a factor stamped before the start is recorded at the start" {
                Attempt(Purpose.Login, START).withVerified(google(START - 1.seconds)).transcript() shouldBe
                    listOf("started Login $START", "identified Google $SUBJECT $START")
            }

            "a factor pulled forward moves the pause by the same amount and no more" {
                val login = PassedPolicy.loginAfterGoogle()
                val behind = Attempt(
                    Purpose.Login,
                    START,
                ).withVerified(google(at(10))).withFailure(at(30)).withFailure(at(29))
                val enrollment = Enrollment(setOf(Totp))

                // Two wrong answers, both at 30 s, owe 1 s then 2 s: the attempt resumes at 32 s.
                login.progressOf(behind, enrollment, now = at(31)).reportTo(Paused) shouldBe at(32)
            }

            "refuses a second account half-way through one person's attempt" {
                val attempt = Attempt(Purpose.Login, START).withVerified(google(at(10)))

                shouldThrow<IllegalArgumentException> { attempt.withVerified(google(at(20), subject = "someone-else")) }
                    .message shouldContain "already belongs to one person"
            }

            "accepts the same account vouched for twice" {
                val attempt = Attempt(Purpose.StepUp, START).withVerified(google(at(10))).withVerified(google(at(20)))

                attempt.transcript().size shouldBe 3
            }

            "refuses a stored history in which a second account appears" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.identified(Google, SUBJECT, at(10))
                        record.identified(Google, "someone-else", at(20))
                    }
                }.message shouldContain "second person"
            }

            "refuses a stored factor told as the wrong word for its kind" {
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.verified(Google, at(10))
                    }
                }.message shouldContain "must say whose"
                shouldThrow<IllegalStateException> {
                    restoring { record ->
                        record.started(Purpose.Login, START)
                        record.identified(Totp, SUBJECT, at(10))
                    }
                }.message shouldContain "names a second person"
            }

            "a complete attempt tells whose account it proved" {
                val attempt = Attempt(Purpose.Login, START).withVerified(google(at(10)))

                PassedPolicy.loginAfterGoogle().progressOf(attempt, Enrollment.Unknown, now = at(11))
                    .reportTo(Whose) shouldBe SUBJECT
            }

            "an attempt that grew without refusal always comes back through restore" {
                val attempt = Attempt(Purpose.Login, START)
                    .withVerified(google(at(10)))
                    .withFailure(at(20))
                    .withFailure(at(20))
                    .withVerified(totp(at(15)))

                Attempt.restore { record -> attempt.writeTo(record) }.transcript() shouldBe attempt.transcript()
            }
        },
    )
