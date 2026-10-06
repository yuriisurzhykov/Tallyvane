package tallyvane.journal.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountId
import tallyvane.journal.domain.Entry
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ME = AccountId(Uuid.parse("0199a000-0000-7000-8000-000000000001"))
private val SOMEONE_ELSE = AccountId(Uuid.parse("0199a000-0000-7000-8000-000000000002"))
private val AT = Instant.parse("2026-10-06T09:00:00Z")

class ShowActivitySpec :
    StringSpec(
        {
            suspend fun outcome(entries: EntriesFake, account: AccountId, before: String? = null) =
                ShowActivityUseCase.ShowActivity(entries, TransactionRunnerFake()).show(account, before)

            suspend fun shown(entries: EntriesFake, account: AccountId, before: String? = null): EntryLines {
                val lines = EntryLines()
                val told = outcome(entries, account, before) as ActivityOutcome.Shown
                told.writeTo(
                    object : ActivityOutcome.Shown.Record, Entry.Record by lines {
                        override fun next(cursor: String?) {
                            lines.next(cursor?.toLong())
                        }
                    },
                )
                return lines
            }

            "shows the person's own entries, the newest first" {
                val entries = EntriesFake()
                entries.add(Entry.totpTurnedOn(ME.value, AT, null))
                entries.add(Entry.totpTurnedOff(ME.value, AT, null))

                shown(entries, ME).lines() shouldBe
                    EntryLines.of(Entry.totpTurnedOff(ME.value, AT, null)) +
                    EntryLines.of(Entry.totpTurnedOn(ME.value, AT, null))
            }

            "does not show the entries of anyone else" {
                val entries = EntriesFake()
                entries.add(Entry.guessingStopped(SOMEONE_ELSE.value, AT))

                shown(entries, ME).lines() shouldBe emptyList()
            }

            "holds thirty entries to a page and names where the next begins" {
                val entries = EntriesFake()
                repeat(31) { entries.add(Entry.recoveryCodeSpent(ME.value, AT, it)) }

                val first = shown(entries, ME)

                first.lines().size shouldBe 30
                shown(entries, ME, before = first.next?.toString()).lines().size shouldBe 1
            }

            "refuses a cursor no page gave" {
                outcome(EntriesFake(), ME, before = "not-a-cursor") shouldBe ActivityOutcome.Failed.UnknownCursor()
                outcome(EntriesFake(), ME, before = "0") shouldBe ActivityOutcome.Failed.UnknownCursor()
                outcome(EntriesFake(), ME, before = "-4") shouldBe ActivityOutcome.Failed.UnknownCursor()
            }
        },
    )
