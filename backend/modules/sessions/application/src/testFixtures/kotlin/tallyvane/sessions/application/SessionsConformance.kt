package tallyvane.sessions.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Session
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ACCOUNT = Uuid.parse("0199a000-0000-7000-8000-0000000000aa")
private val START = Instant.parse("2026-10-02T09:00:00Z")
private val FIRST = Digest(byteArrayOf(1, 2, 3), 1)
private val SECOND = Digest(byteArrayOf(4, 5, 6), 1)

private fun session() = Session.begin(ACCOUNT, setOf(Factor.Google), START, START)

private fun told(session: Session): List<String> {
    val lines = mutableListOf<String>()
    session.writeTo(
        object : Session.Record {
            override fun session(account: Uuid, authenticatedAt: Instant, lastActiveAt: Instant) {
                lines += "session $account $authenticatedAt $lastActiveAt"
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
            val proved = Session.begin(ACCOUNT, setOf(Factor.Google, Factor.Totp), START, START)

            subject.inOwnTransaction { add(FIRST, proved) }

            told(checkNotNull(subject.inOwnTransaction { find(FIRST) })) shouldBe told(proved)
        }

        "tells two sessions apart by their keys" {
            val subject = fresh()
            val other = Session.begin(
                Uuid.parse("0199a000-0000-7000-8000-0000000000bb"),
                setOf(Factor.Totp),
                START,
                START,
            )
            subject.inOwnTransaction { add(FIRST, session()) }
            subject.inOwnTransaction { add(SECOND, other) }

            told(checkNotNull(subject.inOwnTransaction { find(SECOND) })) shouldBe told(other)
        }

        "keeps a key's session only once" {
            val subject = fresh()
            subject.inOwnTransaction { add(FIRST, session()) }

            shouldThrow<IllegalStateException> { subject.inOwnTransaction { add(FIRST, session()) } }
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
            subject.inOwnTransaction { add(SECOND, session()) }

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
    }
}
