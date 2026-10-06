package tallyvane.journal.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountId
import tallyvane.journal.contract.DeviceFacts
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import tallyvane.platform.kernel.ClockFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ME = AccountId(Uuid.parse("0199a000-0000-7000-8000-000000000001"))
private val FIRST_SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000a1")
private val SECOND_SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000a2")
private val AT = Instant.parse("2026-10-06T09:00:00Z")
private val CHROME_ON_WINDOWS = DeviceFacts("chrome", "windows", false, "Work")
private val LABEL = DeviceLabel("chrome", "windows", false, "Work")

class JournalSpec :
    StringSpec(
        {
            fun journal(entries: EntriesFake = EntriesFake(), notifier: SecurityNotifierFake = SecurityNotifierFake()) =
                Triple(Journal(entries, notifier, ClockFake(AT)), entries, notifier)

            fun EntriesFake.lines(): List<String> = EntryLines().also { page(ME.value, null, 30).writeTo(it) }.lines()

            "writes a sign-in with its device, first when the account never signed in from that kind of device" {
                val (journal, entries, _) = journal()

                journal.signedIn(ME, FIRST_SESSION, CHROME_ON_WINDOWS)

                entries.lines() shouldBe
                    EntryLines.of(Entry.signedIn(ME.value, AT, FIRST_SESSION, LABEL, firstFromDevice = true))
            }

            "does not call a second sign-in from the same kind of device first" {
                val (journal, entries, _) = journal()
                journal.signedIn(ME, FIRST_SESSION, CHROME_ON_WINDOWS)

                journal.signedIn(ME, SECOND_SESSION, DeviceFacts("chrome", "windows", false, "Renamed"))

                entries.lines().first() shouldBe EntryLines.of(
                    Entry.signedIn(
                        ME.value,
                        AT,
                        SECOND_SESSION,
                        DeviceLabel("chrome", "windows", false, "Renamed"),
                        firstFromDevice = false,
                    ),
                ).single()
            }

            "names the device of the session on an entry about the second factor" {
                val (journal, entries, _) = journal()
                journal.signedIn(ME, FIRST_SESSION, CHROME_ON_WINDOWS)

                journal.totpTurnedOff(ME, FIRST_SESSION)

                entries.lines().first() shouldBe EntryLines.of(Entry.totpTurnedOff(ME.value, AT, LABEL)).single()
            }

            "writes an entry about the second factor with no device when it has not seen that session sign in" {
                val (journal, entries, _) = journal()

                journal.totpTurnedOn(ME, FIRST_SESSION)

                entries.lines() shouldBe EntryLines.of(Entry.totpTurnedOn(ME.value, AT, null))
            }

            "writes the other things a person does from a session, each with its device" {
                val (journal, entries, _) = journal()
                journal.signedIn(ME, FIRST_SESSION, CHROME_ON_WINDOWS)

                journal.recoveryCodesReissued(ME, FIRST_SESSION)
                journal.otherDevicesSignedOut(ME, FIRST_SESSION)

                entries.lines().take(2) shouldBe
                    EntryLines.of(Entry.otherDevicesSignedOut(ME.value, AT, LABEL)) +
                    EntryLines.of(Entry.recoveryCodesReissued(ME.value, AT, LABEL))
            }

            "writes a spent recovery code with how many are left, and a stopped guess, neither with a device" {
                val (journal, entries, _) = journal()

                journal.recoveryCodeSpent(ME, 7)
                journal.guessingStopped(ME)

                entries.lines() shouldBe
                    EntryLines.of(Entry.guessingStopped(ME.value, AT)) +
                    EntryLines.of(Entry.recoveryCodeSpent(ME.value, AT, 7))
            }

            "tells the notifier every entry it writes, the notable and the quiet alike" {
                val (journal, _, notifier) = journal()

                journal.totpTurnedOn(ME, FIRST_SESSION)
                journal.totpTurnedOff(ME, FIRST_SESSION)

                notifier.told() shouldBe listOf(
                    Entry.totpTurnedOn(ME.value, AT, null),
                    Entry.totpTurnedOff(ME.value, AT, null),
                )
            }
        },
    )
