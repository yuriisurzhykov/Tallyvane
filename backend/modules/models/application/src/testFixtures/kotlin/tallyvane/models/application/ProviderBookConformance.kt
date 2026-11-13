package tallyvane.models.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.models.application.port.ProviderBook
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val OPENAI = Uuid.parse("00000000-0000-7000-8000-0000000000e1")
private val CLAUDE = Uuid.parse("00000000-0000-7000-8000-0000000000e2")

/**
 * The behaviour every [ProviderBook] must show (ADR-046).
 */
abstract class ProviderBookConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val book: ProviderBook
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwn(call: (ProviderBook) -> T): T =
        transactions.inTransaction { Verdict.Commit(call(book)) }

    private fun form(id: Uuid, name: String, key: String?) = ProviderForm(
        id, name, "openai_compatible", "https://api.example.test/v1", key, "small", "big", 0.15, 0.6,
    )

    init {
        "keeps a provider without its key, showing only the key's end" {
            val subject = fresh()

            subject.inOwn { it.save(form(OPENAI, "OpenAI", "sk-secret-1234")) } shouldBe true

            val shown = subject.inOwn { it.list() }.single()
            shown.keyHint shouldBe "…1234"
            shown.active shouldBe false
        }

        "keeps the key when a change brings none" {
            val subject = fresh()
            subject.inOwn { it.save(form(OPENAI, "OpenAI", "sk-secret-1234")) }

            subject.inOwn { it.save(form(OPENAI, "OpenAI renamed", null)) } shouldBe true

            subject.inOwn { it.list() }.single().keyHint shouldBe "…1234"
        }

        "refuses a second provider with a name already taken" {
            val subject = fresh()
            subject.inOwn { it.save(form(OPENAI, "OpenAI", "sk-1")) }

            subject.inOwn { it.save(form(CLAUDE, "OpenAI", "sk-2")) } shouldBe false
        }

        "has exactly one active provider after choosing" {
            val subject = fresh()
            subject.inOwn { it.save(form(OPENAI, "OpenAI", "sk-1")) }
            subject.inOwn { it.save(form(CLAUDE, "Claude", "sk-2")) }

            subject.inOwn { it.activate(OPENAI) } shouldBe true
            subject.inOwn { it.activate(CLAUDE) } shouldBe true
            subject.inOwn { it.activate(Uuid.NIL) } shouldBe false

            subject.inOwn { book -> book.list().filter { it.active }.map { it.id } } shouldBe listOf(CLAUDE)
        }

        "removes a provider" {
            val subject = fresh()
            subject.inOwn { it.save(form(OPENAI, "OpenAI", "sk-1")) }

            subject.inOwn { it.remove(OPENAI) } shouldBe true
            subject.inOwn { it.remove(OPENAI) } shouldBe false
            subject.inOwn { it.costs(Instant.parse("2026-01-01T00:00:00Z")) } shouldBe emptyList()
        }
    }
}
