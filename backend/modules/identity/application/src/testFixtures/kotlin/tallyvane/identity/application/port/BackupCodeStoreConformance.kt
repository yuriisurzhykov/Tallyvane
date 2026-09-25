package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * Shared replacement, availability, and single-use behavior required of recovery-code stores.
 */
public abstract class BackupCodeStoreConformance : StringSpec() {
    protected abstract fun fresh(): Subject

    public interface Subject {
        public val store: BackupCodeStore
        public val transactions: TransactionRunner
    }

    init {
        "replacement exposes only the current generation and consumption is single use" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val first = Secret("first-hash")
            val second = Secret("second-hash")
            val replacement = Secret("replacement-hash")

            subject.transactions.inTransaction {
                subject.store.replace(userId, listOf(first, second))
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction { Verdict.Commit(subject.store.hasAny(userId)) } shouldBe true
            subject.transactions.inTransaction { Verdict.Commit(subject.store.consume(userId, first)) } shouldBe true
            subject.transactions.inTransaction { Verdict.Commit(subject.store.consume(userId, first)) } shouldBe false
            subject.transactions.inTransaction {
                subject.store.replace(userId, listOf(replacement))
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction { Verdict.Commit(subject.store.consume(userId, second)) } shouldBe false
            subject.transactions.inTransaction { Verdict.Commit(subject.store.consume(userId, replacement)) } shouldBe
                true
            subject.transactions.inTransaction { Verdict.Commit(subject.store.hasAny(userId)) } shouldBe false
        }
    }
}
