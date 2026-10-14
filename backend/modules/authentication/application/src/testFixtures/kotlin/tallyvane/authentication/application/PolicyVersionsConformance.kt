package tallyvane.authentication.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val NOW = Instant.parse("2026-10-01T09:00:00Z")

private val GOOGLE = Step(setOf(FactorKind.Google), Step.Necessity.Always)
private val SECOND_FACTOR_IF_ENABLED =
    Step(setOf(FactorKind.Totp, FactorKind.RecoveryCode), Step.Necessity.WhenEnrolled)
private val SECOND_FACTOR_ALWAYS = Step(setOf(FactorKind.Totp, FactorKind.RecoveryCode), Step.Necessity.Always)

/**
 * The behaviour every [PolicyVersions] must show, whatever keeps the versions.
 *
 * Starts from nothing kept, in every implementation: a database that ships with initial policies
 * clears them in [fresh], and a separate spec checks that those are the ones ADR-078 names.
 */
abstract class PolicyVersionsConformance : StringSpec() {
    /**
     * A keeper with no version made and none in force, for any purpose.
     */
    protected abstract suspend fun fresh(): Subject

    /**
     * The port under test, with the transactions its adapter needs around each call.
     */
    interface Subject {
        val versions: PolicyVersions
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: PolicyVersions.() -> T): T =
        transactions.inTransaction { Verdict.Commit(versions.call()) }

    private suspend fun Subject.added(policy: CheckedPolicy, at: Instant = NOW): PolicyVersion =
        inOwnTransaction { add(policy.policy(), SampleChange().change(), at) }

    private suspend fun Subject.inForce(purpose: Purpose): VersionStory? =
        inOwnTransaction { active(purpose) }?.let { VersionStory(it) }

    private suspend fun Subject.historyOf(purpose: Purpose): HistoryStory =
        HistoryStory(inOwnTransaction { history(purpose) })

    private suspend fun Subject.addedBy(policy: CheckedPolicy, change: SampleChange, at: Instant): PolicyVersion =
        inOwnTransaction { add(policy.policy(), change.change(), at) }

    private fun kept(change: SampleChange, at: Instant): String =
        "kept by ${change.author()} because '${change.comment()}' at $at"

    private fun inForceSince(change: SampleChange, at: Instant): String =
        "in force since $at by ${change.author()} because '${change.comment()}'"

    init {
        "has no version in force for any purpose before one is activated" {
            val subject = fresh()

            Purpose.entries.forEach { subject.inForce(it) shouldBe null }
        }

        "numbers the versions of a purpose from 1, one after another" {
            val subject = fresh()

            val policy = CheckedPolicy(Purpose.Login)

            VersionStory(subject.added(policy)) shouldBe VersionStory(PolicyVersion(1, policy.policy()))
            VersionStory(subject.added(policy)) shouldBe VersionStory(PolicyVersion(2, policy.policy()))
            VersionStory(subject.added(policy)) shouldBe VersionStory(PolicyVersion(3, policy.policy()))
        }

        "counts each purpose's versions on its own" {
            val subject = fresh()
            subject.added(CheckedPolicy(Purpose.Login))
            subject.added(CheckedPolicy(Purpose.Login))
            val policy = CheckedPolicy(Purpose.StepUp)

            VersionStory(subject.added(policy)) shouldBe VersionStory(PolicyVersion(1, policy.policy()))
        }

        "hands back the version it kept, telling what the policy told" {
            val subject = fresh()
            val policy = CheckedPolicy(
                purpose = Purpose.Login,
                steps = listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED),
                attemptLifetime = 7.minutes,
                maxFailures = 4,
                firstDelay = 2.seconds,
            )

            VersionStory(subject.added(policy)) shouldBe VersionStory(PolicyVersion(1, policy.policy()))
        }

        "does not put a version in force by keeping it" {
            val subject = fresh()
            subject.added(CheckedPolicy(Purpose.Login))

            subject.inForce(Purpose.Login) shouldBe null
        }

        "puts a version in force when it is activated, and the same version comes back" {
            val subject = fresh()
            val policy = CheckedPolicy(
                purpose = Purpose.AdminLogin,
                steps = listOf(GOOGLE, SECOND_FACTOR_ALWAYS),
                attemptLifetime = 3.minutes,
                maxFailures = 3,
                firstDelay = 10.seconds,
            )
            val version = subject.added(policy)

            subject.inOwnTransaction { activate(version, SampleChange().change(), NOW) }

            subject.inForce(Purpose.AdminLogin) shouldBe VersionStory(version)
        }

        "keeps steps with several kinds in the order they apply, and what each applies to" {
            val subject = fresh()
            val version = subject.added(
                CheckedPolicy(
                    Purpose.AdminLogin,
                    steps = listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED, SECOND_FACTOR_ALWAYS),
                ),
            )
            subject.inOwnTransaction { activate(version, SampleChange().change(), NOW) }

            subject.inForce(Purpose.AdminLogin) shouldBe VersionStory(version)
        }

        "keeps limits to the millisecond" {
            val subject = fresh()
            val policy = CheckedPolicy(
                Purpose.Registration,
                attemptLifetime = 5.minutes + 250.milliseconds,
                firstDelay = 1500.milliseconds,
            )
            val version = subject.added(policy)
            subject.inOwnTransaction { activate(version, SampleChange().change(), NOW) }

            subject.inForce(Purpose.Registration) shouldBe VersionStory(PolicyVersion(1, policy.policy()))
        }

        "refuses to put in force a version that was never kept" {
            val subject = fresh()
            val neverKept = PolicyVersion(1, CheckedPolicy(Purpose.Login).policy())

            shouldThrow<IllegalStateException> {
                subject.inOwnTransaction { activate(neverKept, SampleChange().change(), NOW) }
            }

            subject.inForce(Purpose.Login) shouldBe null
        }

        "refuses to put in force a version built to look like a kept one, with another policy under its number" {
            val subject = fresh()
            val kept = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            subject.inOwnTransaction { activate(kept, SampleChange().change(), NOW) }
            val lookalike = PolicyVersion(1, CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes).policy())

            shouldThrow<IllegalStateException> {
                subject.inOwnTransaction { activate(lookalike, SampleChange().change(), NOW + 1.minutes) }
            }

            subject.inForce(Purpose.Login) shouldBe VersionStory(kept)
        }

        "rolls back by activating an earlier version, and forward again by activating a later one" {
            val subject = fresh()
            val first = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            val second = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))

            subject.inOwnTransaction { activate(first, SampleChange().change(), NOW) }
            subject.inOwnTransaction { activate(second, SampleChange().change(), NOW + 1.minutes) }
            subject.inForce(Purpose.Login) shouldBe VersionStory(second)

            subject.inOwnTransaction { activate(first, SampleChange().change(), NOW + 2.minutes) }
            subject.inForce(Purpose.Login) shouldBe VersionStory(first)

            subject.inOwnTransaction { activate(second, SampleChange().change(), NOW + 3.minutes) }
            subject.inForce(Purpose.Login) shouldBe VersionStory(second)
        }

        "the last activation wins even when two carry the same instant" {
            val subject = fresh()
            val first = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            val second = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))

            subject.inOwnTransaction { activate(first, SampleChange().change(), NOW) }
            subject.inOwnTransaction { activate(second, SampleChange().change(), NOW) }

            subject.inForce(Purpose.Login) shouldBe VersionStory(second)
        }

        "keeps what is in force for each purpose on its own" {
            val subject = fresh()
            val login = subject.added(CheckedPolicy(Purpose.Login))
            val registration = subject.added(CheckedPolicy(Purpose.Registration, maxFailures = 3))

            subject.inOwnTransaction { activate(login, SampleChange().change(), NOW) }
            subject.inOwnTransaction { activate(registration, SampleChange().change(), NOW) }

            subject.inForce(Purpose.Login) shouldBe VersionStory(login)
            subject.inForce(Purpose.Registration) shouldBe VersionStory(registration)
            subject.inForce(Purpose.StepUp) shouldBe null
        }

        "adding a version does not disturb the one in force" {
            val subject = fresh()
            val inForce = subject.added(CheckedPolicy(Purpose.Login))
            subject.inOwnTransaction { activate(inForce, SampleChange().change(), NOW) }

            subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))

            subject.inForce(Purpose.Login) shouldBe VersionStory(inForce)
        }

        "has an empty history before anything is added" {
            val subject = fresh()

            Purpose.entries.forEach { subject.historyOf(it).told() shouldBe emptyList() }
        }

        "lists the versions newest first, each with who made it, when and why" {
            val subject = fresh()
            val ann = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000a"), "First thoughts")
            val bob = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000b"), "Shorter attempts")
            val first = subject.addedBy(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes), ann, NOW)
            val second = subject.addedBy(
                CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes),
                bob,
                NOW + 1.minutes,
            )

            subject.historyOf(Purpose.Login).told() shouldBe
                listOf(kept(bob, NOW + 1.minutes)) + VersionStory(second).told() +
                listOf(kept(ann, NOW)) + VersionStory(first).told()
        }

        "marks the version in force with since when, by whom and why, and no other" {
            val subject = fresh()
            val ann = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000a"), "First thoughts")
            val bob = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000b"), "Go live")
            val first = subject.addedBy(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes), ann, NOW)
            val second = subject.addedBy(
                CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes),
                ann,
                NOW + 1.minutes,
            )

            subject.inOwnTransaction { activate(first, bob.change(), NOW + 2.minutes) }

            subject.historyOf(Purpose.Login).told() shouldBe
                listOf(kept(ann, NOW + 1.minutes)) + VersionStory(second).told() +
                listOf(kept(ann, NOW), inForceSince(bob, NOW + 2.minutes)) + VersionStory(first).told()
        }

        "moves the mark when a later activation rolls back or forward" {
            val subject = fresh()
            val ann = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000a"), "Roll forward")
            val bob = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000b"), "Roll back")
            val first = subject.addedBy(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes), ann, NOW)
            val second = subject.addedBy(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes), ann, NOW)
            subject.inOwnTransaction { activate(second, ann.change(), NOW + 1.minutes) }
            subject.inOwnTransaction { activate(first, bob.change(), NOW + 2.minutes) }

            subject.historyOf(Purpose.Login).told() shouldBe
                listOf(kept(ann, NOW)) + VersionStory(second).told() +
                listOf(kept(ann, NOW), inForceSince(bob, NOW + 2.minutes)) + VersionStory(first).told()
        }

        "keeps the history of each purpose on its own" {
            val subject = fresh()
            val ann = SampleChange()
            val login = subject.addedBy(CheckedPolicy(Purpose.Login), ann, NOW)
            subject.addedBy(CheckedPolicy(Purpose.StepUp), ann, NOW)

            subject.historyOf(Purpose.Login).told() shouldBe listOf(kept(ann, NOW)) + VersionStory(login).told()
            subject.historyOf(Purpose.Registration).told() shouldBe emptyList()
        }

        "finds a version by its number in the history, and none for a number never kept" {
            val subject = fresh()
            val first = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))
            val history = subject.inOwnTransaction { history(Purpose.Login) }

            history.numbered(1)?.let { VersionStory(it) } shouldBe VersionStory(first)
            history.numbered(3) shouldBe null
        }
    }
}
