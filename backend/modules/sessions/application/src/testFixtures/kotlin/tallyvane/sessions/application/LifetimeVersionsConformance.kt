package tallyvane.sessions.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Freshness
import tallyvane.sessions.domain.LifetimeRules
import tallyvane.sessions.domain.LifetimeVersion
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

private val NOW = Instant.parse("2026-10-14T09:00:00Z")

private const val NOBODY = "kept by null because 'null'"

private const val NOBODY_ACTIVATED = "in force by null because 'null'"

private fun session(): Session = Session.begin(
    SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000001")),
    Uuid.parse("0199a000-0000-7000-8000-0000000000aa"),
    setOf(Factor.Google),
    ClientType.Browser,
    UserAgent(null).device(),
    START,
    START,
)

private fun LifetimeRules.freshness(at: Instant): Freshness? = session().standingAt(at, this).reportTo(
    object : Standing.Report<Freshness?> {
        override fun live(
            session: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            authenticatedAt: Instant,
            freshness: Freshness,
        ) = freshness

        override fun endedByIdleness() = null

        override fun endedByAge() = null
    },
)

private class Living : Standing.Report<Boolean> {
    override fun live(
        session: SessionId,
        account: Uuid,
        factors: Set<Factor>,
        authenticatedAt: Instant,
        freshness: Freshness,
    ) = true

    override fun endedByIdleness() = false

    override fun endedByAge() = false
}

private fun adminSession(): Session = Session.begin(
    SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000002")),
    Uuid.parse("0199a000-0000-7000-8000-0000000000ab"),
    setOf(Factor.Google),
    ClientType.Admin,
    UserAgent(null).device(),
    START,
    START,
)

private fun LifetimeRules.lives(at: Instant): Boolean = session().standingAt(at, this).reportTo(Living())

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

    private suspend fun <T> Subject.inOwnTransaction(call: LifetimeVersions.() -> T): T =
        transactions.inTransaction { Verdict.Commit(versions.call()) }

    private suspend fun Subject.added(lifetimes: CheckedLifetimes, change: SampleChange = SampleChange()) =
        inOwnTransaction { add(lifetimes.lifetimes(), change.change(), NOW) }

    private suspend fun Subject.told(client: ClientType): List<String> =
        HistoryStory(inOwnTransaction { history(client) }).told().map(::withoutTimes)

    private fun withoutTimes(line: String): String = line
        .replace(Regex(" at \\d{4}-[^ ]+$"), "")
        .replace(Regex("^in force since [^ ]+ by"), "in force by")

    init {
        "starts with a day idle and a week at most for a browser, as ADR-079 says" {
            val rules = fresh().active()

            rules.lives(START + 1.days - 1.minutes) shouldBe true
            rules.lives(START + 1.days) shouldBe false
        }

        "starts with five minutes of freshness for a browser, as ADR-092 says" {
            val rules = fresh().active()

            rules.freshness(START + 5.minutes - 1.minutes) shouldBe Freshness.Fresh
            rules.freshness(START + 5.minutes) shouldBe Freshness.Stale
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
        "starts with an hour idle and eight hours at most for an administrator" {
            val rules = fresh().active()
            val admin = adminSession()

            admin.standingAt(START + 1.hours - 1.minutes, rules).reportTo(Living()) shouldBe true
            admin.standingAt(START + 1.hours, rules).reportTo(Living()) shouldBe false
        }

        "keeps version 1 of each kind of client as made by nobody and in force by nobody" {
            val subject = fresh()

            ClientType.entries.forEach { client ->
                val told = subject.told(client)
                told[0] shouldBe NOBODY
                told[1] shouldBe NOBODY_ACTIVATED
                told[2] shouldBe "number 1"
                told[3] shouldBe "client $client"
                told.size shouldBe 5
            }
        }

        "numbers the versions of a kind of client one after another, each kind by itself" {
            val subject = fresh()

            VersionStory(subject.added(CheckedLifetimes(ClientType.Browser, idle = 2.days))).told() shouldBe listOf(
                "number 2",
                "client Browser",
                "lifetimes ${2.days} ${7.days} ${5.minutes}",
            )
            NumberOf(subject.added(CheckedLifetimes(ClientType.Browser, idle = 3.days))).number() shouldBe 3
            NumberOf(subject.added(CheckedLifetimes(ClientType.Admin, idle = 2.hours))).number() shouldBe 2
        }

        "does not put a version in force by keeping it" {
            val subject = fresh()
            subject.added(CheckedLifetimes(ClientType.Browser, idle = 2.hours))

            subject.active().lives(START + 1.days - 1.minutes) shouldBe true
        }

        "puts a kept version in force when it is activated, and rolls back by activating an earlier one" {
            val subject = fresh()
            val tighter = subject.added(CheckedLifetimes(ClientType.Browser, idle = 1.hours))

            subject.inOwnTransaction { activate(tighter, SampleChange().change(), NOW) }
            subject.active().lives(START + 2.hours) shouldBe false

            val first = checkNotNull(subject.inOwnTransaction { history(ClientType.Browser) }.numbered(1))
            subject.inOwnTransaction { activate(first, SampleChange().change(), NOW + 1.minutes) }
            subject.active().lives(START + 2.hours) shouldBe true
        }

        "activating a version of one kind of client leaves the other kind as it was" {
            val subject = fresh()
            val longer = subject.added(CheckedLifetimes(ClientType.Admin, idle = 3.hours, absolute = 12.hours))

            subject.inOwnTransaction { activate(longer, SampleChange().change(), NOW) }

            val rules = subject.active()
            rules.lives(START + 2.hours) shouldBe true
            adminSession().standingAt(START + 2.hours, rules).reportTo(Living()) shouldBe true
        }

        "lists the versions newest first, each with who made it and why, and marks the one in force" {
            val subject = fresh()
            val ann = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000a"), "Tighter")
            val bob = SampleChange(Uuid.parse("00000000-0000-4000-8000-00000000000b"), "Go live")
            val second = subject.added(CheckedLifetimes(ClientType.Browser, idle = 1.hours), ann)
            subject.inOwnTransaction { activate(second, bob.change(), NOW + 2.minutes) }

            HistoryStory(subject.inOwnTransaction { history(ClientType.Browser) }).told().take(4) shouldBe listOf(
                "kept by ${ann.author()} because '${ann.comment()}' at $NOW",
                "in force since ${NOW + 2.minutes} by ${bob.author()} because '${bob.comment()}'",
                "number 2",
                "client Browser",
            )
        }

        "finds a version by its number in the history, and none for a number never kept" {
            val subject = fresh()
            val history = subject.inOwnTransaction { history(ClientType.Browser) }

            history.numbered(1)?.let { VersionStory(it).told().first() } shouldBe "number 1"
            history.numbered(2) shouldBe null
        }

        "refuses to put in force a version that was never kept" {
            val subject = fresh()
            val neverKept = LifetimeVersion(9, CheckedLifetimes(ClientType.Browser, idle = 2.hours).lifetimes())

            shouldThrow<IllegalStateException> {
                subject.inOwnTransaction { activate(neverKept, SampleChange().change(), NOW) }
            }

            subject.active().lives(START + 1.days - 1.minutes) shouldBe true
        }

        "refuses to put in force a version built to look like a kept one, with other lifetimes under its number" {
            val subject = fresh()
            val lookalike = LifetimeVersion(1, CheckedLifetimes(ClientType.Browser, idle = 2.hours).lifetimes())

            shouldThrow<IllegalStateException> {
                subject.inOwnTransaction { activate(lookalike, SampleChange().change(), NOW) }
            }
        }
    }
}
