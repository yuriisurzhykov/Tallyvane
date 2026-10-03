package tallyvane.sessions.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val START = Instant.parse("2026-10-03T09:00:00Z")
private val ID = SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000001"))
private val ACCOUNT = Uuid.parse("0199a000-0000-7000-8000-0000000000aa")

private fun session(): Session =
    Session.begin(ID, ACCOUNT, setOf(Factor.Google), ClientType.Browser, UserAgent(null).device(), START, START)

private fun rules(idle: Duration, absolute: Duration): LifetimeRules =
    LifetimeRules.restore { it.lifetimes(ClientType.Browser, idle, absolute) }

private fun Standing.kind(): String = reportTo(
    object : Standing.Report<String> {
        override fun live(session: SessionId, account: Uuid, factors: Set<Factor>, authenticatedAt: Instant) =
            "live ${session.value}"

        override fun endedByIdleness() = "idle"

        override fun endedByAge() = "age"
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
        },
    )
