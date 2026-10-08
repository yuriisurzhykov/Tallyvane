package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.KeptAccounts
import tallyvane.identity.domain.Account
import tallyvane.identity.domain.DisplayName
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val FIRST = Uuid.parse("0199a000-0000-7000-8000-000000000001")
private val SECOND = Uuid.parse("0199a000-0000-7000-8000-000000000002")
private val AT = Instant.parse("2026-10-02T09:00:00Z")

private fun account(id: Uuid, subject: String) =
    Account(id, subject, checkNotNull(DisplayName.of("Ada Lovelace")), "ada@example.com", AT)

/**
 * The behaviour every [KeptAccounts] must show, inherited by the fake and by the adapter over
 * Postgres, so the two cannot quietly disagree (ADR-046).
 */
abstract class KeptAccountsConformance : StringSpec() {
    /**
     * A keeper with nothing kept, and the transactions its calls run in.
     */
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val accounts: KeptAccounts
        val transactions: TransactionRunner

        /**
         * Gives the account [id] the right to administer, the way an operator does: from outside the keeper,
         * because nothing in the system gives it.
         */
        suspend fun grantAdministrator(id: Uuid)
    }

    private suspend fun <T> Subject.inOwnTransaction(call: KeptAccounts.() -> T): T =
        transactions.inTransaction { Verdict.Commit(accounts.call()) }

    init {
        "finds nobody for a subject nobody registered with" {
            fresh().inOwnTransaction { withGoogle("google-1") } shouldBe null
        }

        "keeps an account and finds it by its Google subject" {
            val subject = fresh()

            subject.inOwnTransaction { add(account(FIRST, "google-1")) } shouldBe AccountAdded.Added

            subject.inOwnTransaction { withGoogle("google-1") } shouldBe FIRST
        }

        "keeps one account per Google subject and leaves the first in place" {
            val subject = fresh()
            subject.inOwnTransaction { add(account(FIRST, "google-1")) }

            subject.inOwnTransaction { add(account(SECOND, "google-1")) } shouldBe AccountAdded.SubjectTaken

            subject.inOwnTransaction { withGoogle("google-1") } shouldBe FIRST
        }

        "says who an account is, by the name it was registered with" {
            val subject = fresh()
            subject.inOwnTransaction { add(account(FIRST, "google-1")) }

            val told = mutableListOf<Pair<Uuid, String>>()
            subject.inOwnTransaction { profileOf(FIRST) }?.writeTo { id, name -> told += id to name }

            told shouldBe listOf(FIRST to "Ada Lovelace")
        }

        "says nothing of an account nobody registered" {
            fresh().inOwnTransaction { profileOf(FIRST) } shouldBe null
        }

        "tells two people apart by their subjects" {
            val subject = fresh()
            subject.inOwnTransaction { add(account(FIRST, "google-1")) }
            subject.inOwnTransaction { add(account(SECOND, "google-2")) }

            subject.inOwnTransaction { withGoogle("google-2") } shouldBe SECOND
        }

        "knows an administrator by the right it was given" {
            val subject = fresh()
            subject.inOwnTransaction { add(account(FIRST, "google-1")) }
            subject.grantAdministrator(FIRST)

            subject.inOwnTransaction { isAdministrator(FIRST) } shouldBe true
        }

        "does not take an account for an administrator that was not given the right" {
            val subject = fresh()
            subject.inOwnTransaction { add(account(FIRST, "google-1")) }
            subject.inOwnTransaction { add(account(SECOND, "google-2")) }
            subject.grantAdministrator(FIRST)

            subject.inOwnTransaction { isAdministrator(SECOND) } shouldBe false
        }

        "does not take an account nobody registered for an administrator" {
            fresh().inOwnTransaction { isAdministrator(FIRST) } shouldBe false
        }
    }
}
