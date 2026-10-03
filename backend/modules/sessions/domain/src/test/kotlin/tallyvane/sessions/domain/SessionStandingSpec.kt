package tallyvane.sessions.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val START = Instant.parse("2026-10-03T09:00:00Z")
private val ID = SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000001"))
private val ACCOUNT = Uuid.parse("0199a000-0000-7000-8000-0000000000aa")

private fun session(): Session =
    Session.begin(ID, ACCOUNT, setOf(Factor.Google), ClientType.Browser, UserAgent(null).device(), START, START)

private fun rules(idle: Duration, absolute: Duration, freshness: Duration = 5.minutes): LifetimeRules =
    LifetimeRules.restore { it.lifetimes(ClientType.Browser, idle, absolute, freshness) }

private fun Standing.kind(): String = reportTo(
    object : Standing.Report<String> {
        override fun live(
            session: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            authenticatedAt: Instant,
            freshness: Freshness,
        ) = "live ${session.value}"

        override fun endedByIdleness() = "idle"

        override fun endedByAge() = "age"
    },
)

private fun Standing.freshness(): Freshness = reportTo(
    object : Standing.Report<Freshness> {
        override fun live(
            session: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            authenticatedAt: Instant,
            freshness: Freshness,
        ) = freshness

        override fun endedByIdleness() = error("The session was expected to be live.")

        override fun endedByAge() = error("The session was expected to be live.")
    },
)

private fun Standing.factors(): Set<Factor> = reportTo(
    object : Standing.Report<Set<Factor>> {
        override fun live(
            session: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            authenticatedAt: Instant,
            freshness: Freshness,
        ) = factors

        override fun endedByIdleness() = error("The session was expected to be live.")

        override fun endedByAge() = error("The session was expected to be live.")
    },
)

class SessionStandingSpec :
    StringSpec(
        {
            "a session is live within both lifetimes, and says which session it is" {
                session().standingAt(START + 1.hours, rules(1.days, 7.days)).kind() shouldBe "live ${ID.value}"
            }

            "a session unused for the idle limit is over by idleness" {
                session().standingAt(START + 1.days, rules(1.days, 7.days)).kind() shouldBe "idle"
            }

            "a session that has lived the absolute limit is over by age, however recently it was used" {
                session().seenAt(
                    START + 7.days - 1.hours,
                ).standingAt(START + 7.days, rules(1.days, 7.days)).kind() shouldBe
                    "age"
            }

            "tightening the rules ends a session that was live a moment before, without touching the session" {
                val kept = session()
                val moment = START + 2.hours

                kept.standingAt(moment, rules(1.days, 7.days)).kind() shouldBe "live ${ID.value}"
                kept.standingAt(moment, rules(1.hours, 7.days)).kind() shouldBe "idle"
            }

            "a session named by its person keeps its id and its lifetimes" {
                val named = session().renamed(DeviceName("Work laptop"))

                named.isIdentifiedBy(ID) shouldBe true
                named.standingAt(START + 1.hours, rules(1.days, 7.days)).kind() shouldBe "live ${ID.value}"
            }

            "a device name is trimmed, one to sixty characters, and has no control characters" {
                DeviceName.accepts("  Work laptop ") shouldBe true
                DeviceName.accepts("   ") shouldBe false
                DeviceName.accepts("x".repeat(60)) shouldBe true
                DeviceName.accepts("x".repeat(61)) shouldBe false
                DeviceName.accepts("bad\nname") shouldBe false
            }

            "a session that was just begun is fresh: signing in is itself the proof" {
                session().standingAt(START + 4.minutes, rules(1.days, 7.days)).freshness() shouldBe Freshness.Fresh
            }

            "a proof goes stale once its freshness has passed, and is stale exactly at that moment" {
                val rules = rules(1.days, 7.days, freshness = 5.minutes)

                session().standingAt(START + 5.minutes - 1.seconds, rules).freshness() shouldBe Freshness.Fresh
                session().standingAt(START + 5.minutes, rules).freshness() shouldBe Freshness.Stale
            }

            "using a session does not make it fresh: only proving who the person is does" {
                val used = session().seenAt(START + 20.minutes)

                used.standingAt(START + 20.minutes, rules(1.days, 7.days)).freshness() shouldBe Freshness.Stale
            }

            "confirming a stale session makes it fresh again from the moment of the proof" {
                val confirmed = session().confirmed(START + 1.hours, setOf(Factor.Google))

                confirmed.standingAt(START + 1.hours + 4.minutes, rules(1.days, 7.days)).freshness() shouldBe
                    Freshness.Fresh
                confirmed.standingAt(START + 1.hours + 5.minutes, rules(1.days, 7.days)).freshness() shouldBe
                    Freshness.Stale
            }

            "confirming never moves the proof back, so a slow request cannot make a session staler" {
                val confirmed = session().confirmed(START + 1.hours, setOf(Factor.Google)).confirmed(
                    START + 30.minutes,
                    setOf(Factor.Google),
                )

                confirmed.standingAt(START + 1.hours + 1.minutes, rules(1.days, 7.days)).freshness() shouldBe
                    Freshness.Fresh
            }

            "confirming adds the factors that proved it to those the session records" {
                val confirmed = session().confirmed(START + 1.hours, setOf(Factor.Google, Factor.Totp))

                confirmed.standingAt(START + 1.hours, rules(1.days, 7.days)).factors() shouldBe
                    setOf(Factor.Google, Factor.Totp)
            }

            "confirming does not make a session live longer: the absolute lifetime still counts from the sign-in" {
                val confirmed = session().confirmed(START + 7.days - 1.minutes, setOf(Factor.Google))
                    .seenAt(START + 7.days - 1.minutes)

                confirmed.standingAt(START + 7.days, rules(1.days, 7.days)).kind() shouldBe "age"
            }

            "a session restored with a confirmation before its sign-in is refused, as no session could have told it" {
                shouldThrow<IllegalStateException> {
                    Session.restore { record ->
                        record.session(ID, ACCOUNT, ClientType.Browser, START, START - 1.minutes, START)
                        record.device(Browser.Other, Platform.Other, false, null)
                        record.proved(Factor.Google)
                    }
                }
            }
        },
    )
