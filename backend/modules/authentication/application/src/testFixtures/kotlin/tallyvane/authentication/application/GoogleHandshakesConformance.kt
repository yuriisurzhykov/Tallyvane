package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleHandshakes
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant

private val FIRST = KeysForTests().of("first")
private val SECOND = KeysForTests().of("second")
private val START = Instant.parse("2026-10-02T09:00:00Z")

private fun handshake(word: String) =
    GoogleHandshake(Secret("state-$word"), Secret("nonce-$word"), Secret("verifier-$word"))

private fun GoogleHandshake.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo { state, nonce, verifier -> told += listOf(state.revealed(), nonce.revealed(), verifier.revealed()) }
    return told
}

/**
 * The behaviour every [GoogleHandshakes] must show, whatever keeps them.
 *
 * A handshake is judged by what it tells through [GoogleHandshake.writeTo], since it has no `equals`
 * and gives out no fields.
 */
abstract class GoogleHandshakesConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val attempts: Attempts
        val handshakes: GoogleHandshakes
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: () -> T): T =
        transactions.inTransaction { Verdict.Commit(call()) }

    private suspend fun Subject.attemptKept(key: Digest) =
        inOwnTransaction { attempts.save(key, Attempt(Purpose.Login, START)) }

    init {
        "takes nothing under a key nothing was kept for" {
            val subject = fresh()

            subject.inOwnTransaction { subject.handshakes.take(FIRST) } shouldBe null
        }

        "hands back what was kept, once" {
            val subject = fresh()
            subject.attemptKept(FIRST)
            subject.inOwnTransaction { subject.handshakes.keep(FIRST, handshake("a")) }

            val first = subject.inOwnTransaction { subject.handshakes.take(FIRST) }
            val second = subject.inOwnTransaction { subject.handshakes.take(FIRST) }

            first?.told() shouldBe handshake("a").told()
            second shouldBe null
        }

        "keeps each attempt's handshake apart" {
            val subject = fresh()
            subject.attemptKept(FIRST)
            subject.attemptKept(SECOND)
            subject.inOwnTransaction { subject.handshakes.keep(FIRST, handshake("a")) }
            subject.inOwnTransaction { subject.handshakes.keep(SECOND, handshake("b")) }

            subject.inOwnTransaction { subject.handshakes.take(SECOND) }?.told() shouldBe handshake("b").told()
            subject.inOwnTransaction { subject.handshakes.take(FIRST) }?.told() shouldBe handshake("a").told()
        }

        "goes when its attempt is forgotten" {
            val subject = fresh()
            subject.attemptKept(FIRST)
            subject.inOwnTransaction { subject.handshakes.keep(FIRST, handshake("a")) }

            subject.inOwnTransaction { subject.attempts.forget(FIRST) }

            subject.inOwnTransaction { subject.handshakes.take(FIRST) } shouldBe null
        }
    }
}
