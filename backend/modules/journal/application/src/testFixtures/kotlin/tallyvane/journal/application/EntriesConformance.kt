package tallyvane.journal.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.journal.application.port.Entries
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ME = Uuid.parse("0199a000-0000-7000-8000-000000000001")
private val SOMEONE_ELSE = Uuid.parse("0199a000-0000-7000-8000-000000000002")
private val FIRST_SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000a1")
private val SECOND_SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000a2")
private val AT = Instant.parse("2026-10-06T09:00:00Z")
private val CHROME_ON_WINDOWS = DeviceLabel("chrome", "windows", false, "Work")
private val SAFARI_ON_IPHONE = DeviceLabel("safari", "ios", true, null)

/**
 * The behaviour every [Entries] must show, inherited by the fake and by the adapter over Postgres, so the two
 * cannot quietly disagree (ADR-046).
 */
abstract class EntriesConformance : StringSpec() {
    /**
     * A journal with nothing in it, and the transactions its calls run in.
     */
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val entries: Entries
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: Entries.() -> T): T =
        transactions.inTransaction { Verdict.Commit(entries.call()) }

    private suspend fun Subject.pageOf(account: Uuid, before: Long? = null, limit: Int = 30): EntryLines =
        inOwnTransaction { page(account, before, limit) }.let { page -> EntryLines().also { page.writeTo(it) } }

    init {
        "has no entries for an account nobody wrote about" {
            val page = fresh().pageOf(ME)

            page.lines() shouldBe emptyList()
            page.next shouldBe null
        }

        "keeps an entry and tells it back, with its device" {
            val subject = fresh()
            val entry = Entry.signedIn(ME, AT, FIRST_SESSION, CHROME_ON_WINDOWS, firstFromDevice = true)

            subject.inOwnTransaction { add(entry) }

            subject.pageOf(ME).lines() shouldBe EntryLines.of(entry)
        }

        "keeps an entry that has no device, and tells none" {
            val subject = fresh()
            val entry = Entry.recoveryCodeSpent(ME, AT, 7)

            subject.inOwnTransaction { add(entry) }

            subject.pageOf(ME).lines() shouldBe EntryLines.of(entry)
        }

        "tells the newest entry first" {
            val subject = fresh()
            val older = Entry.totpTurnedOn(ME, AT, null)
            val newer = Entry.totpTurnedOff(ME, AT, null)
            subject.inOwnTransaction { add(older) }
            subject.inOwnTransaction { add(newer) }

            subject.pageOf(ME).lines() shouldBe EntryLines.of(newer) + EntryLines.of(older)
        }

        "tells only the entries of the account asked about" {
            val subject = fresh()
            subject.inOwnTransaction { add(Entry.guessingStopped(SOMEONE_ELSE, AT)) }
            val mine = Entry.guessingStopped(ME, AT)
            subject.inOwnTransaction { add(mine) }

            subject.pageOf(ME).lines() shouldBe EntryLines.of(mine)
        }

        "pages: a full page names where the next begins, and the next page carries on without repeating" {
            val subject = fresh()
            val made = (1..5).map { Entry.recoveryCodeSpent(ME, AT, it) }
            made.forEach { entry -> subject.inOwnTransaction { add(entry) } }
            val newestFirst = made.reversed().map { EntryLines.of(it).single() }

            val first = subject.pageOf(ME, limit = 2)
            val second = subject.pageOf(ME, before = first.next, limit = 2)
            val third = subject.pageOf(ME, before = second.next, limit = 2)

            first.lines() shouldBe newestFirst.subList(0, 2)
            second.lines() shouldBe newestFirst.subList(2, 4)
            third.lines() shouldBe newestFirst.subList(4, 5)
            third.next shouldBe null
        }

        "does not name a next page when the last entry just fits the page" {
            val subject = fresh()
            repeat(2) { subject.inOwnTransaction { add(Entry.guessingStopped(ME, AT)) } }

            subject.pageOf(ME, limit = 2).next shouldBe null
        }

        "finds the device of the sign-in that began a session" {
            val subject = fresh()
            subject.inOwnTransaction {
                add(Entry.signedIn(ME, AT, FIRST_SESSION, CHROME_ON_WINDOWS, firstFromDevice = true))
            }
            subject.inOwnTransaction {
                add(Entry.signedIn(ME, AT, SECOND_SESSION, SAFARI_ON_IPHONE, firstFromDevice = true))
            }

            subject.inOwnTransaction { deviceOf(FIRST_SESSION) } shouldBe CHROME_ON_WINDOWS
            subject.inOwnTransaction { deviceOf(SECOND_SESSION) } shouldBe SAFARI_ON_IPHONE
        }

        "finds no device for a session the journal has no sign-in of" {
            fresh().inOwnTransaction { deviceOf(FIRST_SESSION) } shouldBe null
        }

        "has seen a sign-in from the same kind of device, whatever its name" {
            val subject = fresh()
            subject.inOwnTransaction {
                add(Entry.signedIn(ME, AT, FIRST_SESSION, CHROME_ON_WINDOWS, firstFromDevice = true))
            }

            subject.inOwnTransaction {
                hasSignedInFrom(ME, DeviceLabel("chrome", "windows", false, "Another name"))
            } shouldBe
                true
        }

        "has not seen a sign-in from a different browser, system or class of device" {
            val subject = fresh()
            subject.inOwnTransaction {
                add(Entry.signedIn(ME, AT, FIRST_SESSION, CHROME_ON_WINDOWS, firstFromDevice = true))
            }

            subject.inOwnTransaction { hasSignedInFrom(ME, DeviceLabel("firefox", "windows", false, null)) } shouldBe
                false
            subject.inOwnTransaction { hasSignedInFrom(ME, DeviceLabel("chrome", "macos", false, null)) } shouldBe false
            subject.inOwnTransaction { hasSignedInFrom(ME, DeviceLabel("chrome", "windows", true, null)) } shouldBe
                false
        }

        "has not seen a sign-in of another account from the same kind of device" {
            val subject = fresh()
            subject.inOwnTransaction {
                add(Entry.signedIn(SOMEONE_ELSE, AT, FIRST_SESSION, CHROME_ON_WINDOWS, firstFromDevice = true))
            }

            subject.inOwnTransaction { hasSignedInFrom(ME, CHROME_ON_WINDOWS) } shouldBe false
        }
    }
}
