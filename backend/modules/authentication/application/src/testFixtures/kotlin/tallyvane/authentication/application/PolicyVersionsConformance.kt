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
        inOwnTransaction { add(policy.policy(), at) }

    private suspend fun Subject.inForce(purpose: Purpose): VersionStory? =
        inOwnTransaction { active(purpose) }?.let { VersionStory(it) }

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

            subject.inOwnTransaction { activate(version, NOW) }

            subject.inForce(Purpose.AdminLogin) shouldBe VersionStory(version)
        }

        "keeps steps with several kinds in the order they apply, and what each applies to" {
            val subject = fresh()
            val version = subject.added(
                CheckedPolicy(Purpose.Login, steps = listOf(GOOGLE, SECOND_FACTOR_IF_ENABLED, SECOND_FACTOR_ALWAYS)),
            )
            subject.inOwnTransaction { activate(version, NOW) }

            subject.inForce(Purpose.Login) shouldBe VersionStory(version)
        }

        "keeps limits to the millisecond" {
            val subject = fresh()
            val policy = CheckedPolicy(
                Purpose.Registration,
                attemptLifetime = 5.minutes + 250.milliseconds,
                firstDelay = 1500.milliseconds,
            )
            val version = subject.added(policy)
            subject.inOwnTransaction { activate(version, NOW) }

            subject.inForce(Purpose.Registration) shouldBe VersionStory(PolicyVersion(1, policy.policy()))
        }

        "refuses to put in force a version that was never kept" {
            val subject = fresh()
            val neverKept = PolicyVersion(1, CheckedPolicy(Purpose.Login).policy())

            shouldThrow<IllegalStateException> { subject.inOwnTransaction { activate(neverKept, NOW) } }

            subject.inForce(Purpose.Login) shouldBe null
        }

        "refuses to put in force a version built to look like a kept one, with another policy under its number" {
            val subject = fresh()
            val kept = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            subject.inOwnTransaction { activate(kept, NOW) }
            val lookalike = PolicyVersion(1, CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes).policy())

            shouldThrow<IllegalStateException> { subject.inOwnTransaction { activate(lookalike, NOW + 1.minutes) } }

            subject.inForce(Purpose.Login) shouldBe VersionStory(kept)
        }

        "rolls back by activating an earlier version, and forward again by activating a later one" {
            val subject = fresh()
            val first = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            val second = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))

            subject.inOwnTransaction { activate(first, NOW) }
            subject.inOwnTransaction { activate(second, NOW + 1.minutes) }
            subject.inForce(Purpose.Login) shouldBe VersionStory(second)

            subject.inOwnTransaction { activate(first, NOW + 2.minutes) }
            subject.inForce(Purpose.Login) shouldBe VersionStory(first)

            subject.inOwnTransaction { activate(second, NOW + 3.minutes) }
            subject.inForce(Purpose.Login) shouldBe VersionStory(second)
        }

        "the last activation wins even when two carry the same instant" {
            val subject = fresh()
            val first = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 5.minutes))
            val second = subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))

            subject.inOwnTransaction { activate(first, NOW) }
            subject.inOwnTransaction { activate(second, NOW) }

            subject.inForce(Purpose.Login) shouldBe VersionStory(second)
        }

        "keeps what is in force for each purpose on its own" {
            val subject = fresh()
            val login = subject.added(CheckedPolicy(Purpose.Login))
            val registration = subject.added(CheckedPolicy(Purpose.Registration, maxFailures = 3))

            subject.inOwnTransaction { activate(login, NOW) }
            subject.inOwnTransaction { activate(registration, NOW) }

            subject.inForce(Purpose.Login) shouldBe VersionStory(login)
            subject.inForce(Purpose.Registration) shouldBe VersionStory(registration)
            subject.inForce(Purpose.StepUp) shouldBe null
        }

        "adding a version does not disturb the one in force" {
            val subject = fresh()
            val inForce = subject.added(CheckedPolicy(Purpose.Login))
            subject.inOwnTransaction { activate(inForce, NOW) }

            subject.added(CheckedPolicy(Purpose.Login, attemptLifetime = 4.minutes))

            subject.inForce(Purpose.Login) shouldBe VersionStory(inForce)
        }
    }
}
