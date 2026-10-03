package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.LifetimeRules
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.Standing
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val START = Instant.parse("2026-10-03T09:00:00Z")

private fun session(): Session = Session.begin(
    SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000001")),
    Uuid.parse("0199a000-0000-7000-8000-0000000000aa"),
    setOf(Factor.Google),
    ClientType.Browser,
    UserAgent(null).device(),
    START,
    START,
)

private fun LifetimeRules.lives(at: Instant): Boolean = session().standingAt(at, this).reportTo(
    object : Standing.Report<Boolean> {
        override fun live(session: SessionId, account: Uuid, factors: Set<Factor>, authenticatedAt: Instant) = true

        override fun endedByIdleness() = false

        override fun endedByAge() = false
    },
)

/**
 * The behaviour every [LifetimeVersions] must show, inherited by the fake and by the adapter over
 * Postgres, so the two cannot quietly disagree (ADR-046).
 */
abstract class LifetimeVersionsConformance : StringSpec() {
    /**
     * What the migration puts in force and nothing else, and the transactions its reads run in.
     */
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val versions: LifetimeVersions
        val transactions: TransactionRunner

        /**
         * Makes a new version in force that gives a browser [idle] and [absolute], as an admin would.
         */
        suspend fun activate(idle: Duration, absolute: Duration)
    }

    private suspend fun Subject.active(): LifetimeRules = transactions.inTransaction {
        Verdict.Commit(versions.active())
    }

    init {
        "starts with a day idle and a week at most for a browser, as ADR-079 says" {
            val rules = fresh().active()

            rules.lives(START + 1.days - 1.minutes) shouldBe true
            rules.lives(START + 1.days) shouldBe false
        }

        "takes the version activated last, so a tighter one is in force at the next read" {
            val subject = fresh()
            subject.activate(1.hours, 7.days)

            val rules = subject.active()

            rules.lives(START + 2.hours) shouldBe false
            rules.lives(START + 30.minutes) shouldBe true
        }

        "lets the version in force be loosened as well" {
            val subject = fresh()
            subject.activate(1.hours, 7.days)
            subject.activate(3.days, 30.days)

            val rules = subject.active()

            rules.lives(START + 2.days) shouldBe true
        }
    }
}
