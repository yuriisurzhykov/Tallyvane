package tallyvane.sessions.infrastructure

import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import tallyvane.sessions.application.SessionsConformance
import tallyvane.sessions.application.confirmedAtOf
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val BEGUN = Instant.parse("2026-10-02T09:00:00Z")

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046), and by what only a
 * database can show: a session written by the release before confirming existed.
 */
class PostgresSessionsIntegrationSpec : SessionsConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }

        "a session written without a last confirmation, as the release before it did, is last confirmed when it began" {
            val subject = fresh()
            val key = Digest(byteArrayOf(1, 2, 3), 1)
            val begun = Session.begin(
                SessionId(Uuid.parse("0199a000-0000-7000-8000-000000000001")),
                Uuid.parse("0199a000-0000-7000-8000-0000000000aa"),
                setOf(Factor.Google),
                ClientType.Browser,
                UserAgent(null).device(),
                BEGUN,
                BEGUN,
            )
            subject.transactions.inTransaction {
                subject.sessions.add(key, begun)
                TransactionManager.current().exec("update sessions.sessions set confirmed_at = null")
                Verdict.Commit(Unit)
            }

            val read = subject.transactions.inTransaction { Verdict.Commit(subject.sessions.find(key)) }

            confirmedAtOf(checkNotNull(read)) shouldBe BEGUN
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        return object : Subject {
            override val sessions: Sessions = SessionsStorageFactory().sessions()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
