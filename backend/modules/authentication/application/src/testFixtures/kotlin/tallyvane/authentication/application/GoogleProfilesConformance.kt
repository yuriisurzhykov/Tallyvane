package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant

private val FIRST = KeysForTests().of("first")
private val SECOND = KeysForTests().of("second")
private val START = Instant.parse("2026-10-02T09:00:00Z")

private fun GoogleProfile.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo { name, email -> told += listOf(name, email) }
    return told
}

/**
 * The behaviour every [GoogleProfiles] must show, whatever keeps them.
 */
abstract class GoogleProfilesConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val attempts: Attempts
        val profiles: GoogleProfiles
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: () -> T): T =
        transactions.inTransaction { Verdict.Commit(call()) }

    private suspend fun Subject.attemptKept(key: Digest) =
        inOwnTransaction { attempts.save(key, Attempt(Purpose.Registration, START)) }

    init {
        "finds nothing under a key nothing was kept for" {
            val subject = fresh()

            subject.inOwnTransaction { subject.profiles.of(FIRST) } shouldBe null
        }

        "finds what was kept, as often as asked" {
            val subject = fresh()
            subject.attemptKept(FIRST)
            subject.inOwnTransaction { subject.profiles.keep(FIRST, GoogleProfile("Ann", "ann@example.com")) }

            subject.inOwnTransaction { subject.profiles.of(FIRST) }?.told() shouldBe listOf("Ann", "ann@example.com")
            subject.inOwnTransaction { subject.profiles.of(FIRST) }?.told() shouldBe listOf("Ann", "ann@example.com")
        }

        "keeps each attempt's profile apart" {
            val subject = fresh()
            subject.attemptKept(FIRST)
            subject.attemptKept(SECOND)
            subject.inOwnTransaction { subject.profiles.keep(FIRST, GoogleProfile("Ann", "ann@example.com")) }
            subject.inOwnTransaction { subject.profiles.keep(SECOND, GoogleProfile("Bob", "bob@example.com")) }

            subject.inOwnTransaction { subject.profiles.of(SECOND) }?.told() shouldBe listOf("Bob", "bob@example.com")
        }

        "goes when its attempt is forgotten" {
            val subject = fresh()
            subject.attemptKept(FIRST)
            subject.inOwnTransaction { subject.profiles.keep(FIRST, GoogleProfile("Ann", "ann@example.com")) }

            subject.inOwnTransaction { subject.attempts.forget(FIRST) }

            subject.inOwnTransaction { subject.profiles.of(FIRST) } shouldBe null
        }
    }
}
