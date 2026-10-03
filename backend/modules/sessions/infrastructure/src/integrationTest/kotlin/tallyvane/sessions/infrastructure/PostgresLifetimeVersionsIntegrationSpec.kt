package tallyvane.sessions.infrastructure

import io.kotest.assertions.throwables.shouldThrow
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import tallyvane.sessions.application.LifetimeVersionsConformance
import tallyvane.sessions.application.port.LifetimeVersions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private const val ONE_DAY_IN_MILLIS = 86_400_000

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046), and by what only a
 * database can promise: the bounds are checked when a version is read, and history cannot be rewritten.
 */
class PostgresLifetimeVersionsIntegrationSpec : LifetimeVersionsConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }

        "refuses to read a version in force that is outside the bounds the code sets" {
            val subject = fresh()
            subject.activate(1.minutes, 7.days)

            shouldThrow<IllegalArgumentException> {
                subject.transactions.inTransaction { Verdict.Commit(subject.versions.active()) }
            }
        }

        "refuses to rewrite history: a version and an activation can only be added" {
            val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }

            shouldThrow<Exception> {
                persistence.transactions.inTransaction {
                    TransactionManager.current().exec(
                        "update sessions.lifetime_versions set idle_millis = $ONE_DAY_IN_MILLIS",
                    )
                    Verdict.Commit(Unit)
                }
            }
            shouldThrow<Exception> {
                persistence.transactions.inTransaction {
                    TransactionManager.current().exec("delete from sessions.lifetime_activations")
                    Verdict.Commit(Unit)
                }
            }
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        return object : Subject {
            override val versions: LifetimeVersions = SessionsStorageFactory().lifetimeVersions()
            override val transactions: TransactionRunner = persistence.transactions

            override suspend fun activate(idle: Duration, absolute: Duration) {
                persistence.transactions.inTransaction {
                    TransactionManager.current().exec(
                        """
                        insert into sessions.lifetime_versions (client_type, number, idle_millis, absolute_millis, freshness_millis, created_at)
                        select 'browser', coalesce(max(number), 0) + 1, ${idle.inWholeMilliseconds}, ${absolute.inWholeMilliseconds}, 300000, now()
                          from sessions.lifetime_versions where client_type = 'browser';
                        insert into sessions.lifetime_activations (client_type, number, activated_at)
                        select 'browser', max(number), now() from sessions.lifetime_versions where client_type = 'browser';
                        """.trimIndent(),
                    )
                    Verdict.Commit(Unit)
                }
            }
        }
    }
}
