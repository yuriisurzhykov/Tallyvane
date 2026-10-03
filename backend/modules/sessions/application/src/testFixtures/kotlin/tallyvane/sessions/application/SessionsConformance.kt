package tallyvane.sessions.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.DeviceName
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Platform
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ACCOUNT = Uuid.parse("0199a000-0000-7000-8000-0000000000aa")
private val START = Instant.parse("2026-10-02T09:00:00Z")
private val FIRST = Digest(byteArrayOf(1, 2, 3), 1)
private val SECOND = Digest(byteArrayOf(4, 5, 6), 1)
private val THIRD = Digest(byteArrayOf(7, 8, 9), 1)

private val OTHER_ACCOUNT = Uuid.parse("0199a000-0000-7000-8000-0000000000bb")
private val FIRST_ID = SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000001"))
private val SECOND_ID = SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000002"))
private val THIRD_ID = SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000003"))
private val DEVICE = UserAgent(
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
).device()

private fun session(id: SessionId = FIRST_ID, account: Uuid = ACCOUNT, factors: Set<Factor> = setOf(Factor.Google)) =
    Session.begin(id, account, factors, ClientType.Browser, DEVICE, START, START)

private fun told(session: Session): List<String> {
    val lines = mutableListOf<String>()
    session.writeTo(
        object : Session.Record {
            override fun session(
                id: SessionId,
                account: Uuid,
                client: ClientType,
                authenticatedAt: Instant,
                lastActiveAt: Instant,
            ) {
                lines += "session ${id.value} $account $client $authenticatedAt $lastActiveAt"
            }

            override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) {
                lines += "device $browser $platform $mobile $name"
            }

            override fun proved(factor: Factor) {
                lines += "proved $factor"
            }
        },
    )
    return lines
}

/**
 * The behaviour every [Sessions] must show, inherited by the fake and by the adapter over Postgres, so
 * the two cannot quietly disagree (ADR-046).
 */
abstract class SessionsConformance : StringSpec() {
    /**
     * A keeper with nothing kept, and the transactions its calls run in.
     */
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val sessions: Sessions
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: Sessions.() -> T): T =
        transactions.inTransaction { Verdict.Commit(sessions.call()) }

    init {
        "finds nothing under a key nobody kept a session under" {
            fresh().inOwnTransaction { find(FIRST) } shouldBe null
        }

        "keeps a session and brings back what it was told" {
            val subject = fresh()
            val kept = session()

            subject.inOwnTransaction { add(FIRST, kept) }

            told(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe told(kept)
        }

        "keeps every way the person proved who they are" {
            val subject = fresh()
            val proved = session(factors = setOf(Factor.Google, Factor.Totp))

            subject.inOwnTransaction { add(FIRST, proved) }

            told(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe told(proved)
        }

        "tells two sessions apart by their keys" {
            val subject = fresh()
            val other = session(id = SECOND_ID, account = OTHER_ACCOUNT, factors = setOf(Factor.Totp))
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { add(SECOND, other) }

            told(checkNotNull(subject.inOwnTransaction { find(SECOND) })) shouldBe told(other)
        }

        "keeps a key's session only once" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            shouldThrow<IllegalStateException> { subject.inOwnTransaction { add(FIRST, session(id = SECOND_ID)) } }
        }

        "finds a key with another pepper version as another key" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { find(Digest(byteArrayOf(1, 2, 3), 2)) } shouldBe null
        }

        "forgets a session, and the next lookup finds none" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { forget(FIRST) }

            subject.inOwnTransaction { find(FIRST) } shouldBe null
        }

        "forgets only the session it was told to" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { add(SECOND, session(id = SECOND_ID)) }

            subject.inOwnTransaction { forget(FIRST) }

            subject.inOwnTransaction { find(SECOND) }.let { it != null } shouldBe true
        }

        "forgetting a session nobody kept changes nothing" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { forget(SECOND) }

            subject.inOwnTransaction { find(FIRST) }.let { it != null } shouldBe true
        }

        "notes use that is a grain later than the last" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }
            val later = START + Session.USE_GRAIN

            subject.inOwnTransaction { saw(FIRST, later) }

            lastUseOf(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe later
        }

        "does not note use that is less than a grain after the last" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { saw(FIRST, START + Session.USE_GRAIN - 1.seconds) }

            lastUseOf(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe START
        }

        "does not move last use back" {
            val subject = fresh()
            val used = START + Session.USE_GRAIN + Session.USE_GRAIN
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { saw(FIRST, used) }

            subject.inOwnTransaction { saw(FIRST, START) }

            lastUseOf(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe used
        }

        "noting use of a session nobody kept changes nothing" {
            val subject = fresh()

            subject.inOwnTransaction { saw(FIRST, START + Session.USE_GRAIN) }

            subject.inOwnTransaction { find(FIRST) } shouldBe null
        }

        "keeps the device a session was begun on, as parts" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            told(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe told(session())
        }

        "lists the sessions of an account and no others" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { add(SECOND, session(id = SECOND_ID)) }
            subject.inOwnTransaction { add(THIRD, session(id = THIRD_ID, account = OTHER_ACCOUNT)) }

            subject.inOwnTransaction { ofAccount(ACCOUNT) }.map(::told).toSet() shouldBe
                setOf(told(session()), told(session(id = SECOND_ID)))
        }

        "lists nothing for an account that has no session" {
            fresh().inOwnTransaction { ofAccount(ACCOUNT) } shouldBe emptyList()
        }

        "revokes a session by its id, and its key finds nothing afterwards" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { revoke(ACCOUNT, FIRST_ID) } shouldBe true

            subject.inOwnTransaction { find(FIRST) } shouldBe null
        }

        "does not revoke a session of another account, however its id is known" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { revoke(OTHER_ACCOUNT, FIRST_ID) } shouldBe false

            subject.inOwnTransaction { find(FIRST) }.let { it != null } shouldBe true
        }

        "revoking an id nobody kept changes nothing and says so" {
            fresh().inOwnTransaction { revoke(ACCOUNT, FIRST_ID) } shouldBe false
        }

        "revokes every session of an account but the one kept" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { add(SECOND, session(id = SECOND_ID)) }
            subject.inOwnTransaction { add(THIRD, session(id = THIRD_ID, account = OTHER_ACCOUNT)) }

            subject.inOwnTransaction { revokeOthers(ACCOUNT, FIRST_ID) }

            subject.inOwnTransaction { find(FIRST) }.let { it != null } shouldBe true
            subject.inOwnTransaction { find(SECOND) } shouldBe null
            subject.inOwnTransaction { find(THIRD) }.let { it != null } shouldBe true
        }

        "revokes every session of an account" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { add(SECOND, session(id = SECOND_ID)) }
            subject.inOwnTransaction { add(THIRD, session(id = THIRD_ID, account = OTHER_ACCOUNT)) }

            subject.inOwnTransaction { revokeAll(ACCOUNT) }

            subject.inOwnTransaction { find(FIRST) } shouldBe null
            subject.inOwnTransaction { find(SECOND) } shouldBe null
            subject.inOwnTransaction { find(THIRD) }.let { it != null } shouldBe true
        }

        "names a device, and the name comes back with the session" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { rename(ACCOUNT, FIRST_ID, DeviceName("Work laptop")) } shouldBe true

            told(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe
                told(session().renamed(DeviceName("Work laptop")))
        }

        "does not name a device of another account" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            subject.inOwnTransaction { rename(OTHER_ACCOUNT, FIRST_ID, DeviceName("Mine now")) } shouldBe false

            told(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe told(session())
        }
    }
}
