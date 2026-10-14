package tallyvane.sessions.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import tallyvane.sessions.application.CheckedLifetimes
import tallyvane.sessions.application.SampleChange
import tallyvane.sessions.domain.ClientType
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-14T09:00:00Z")

/**
 * Two administrators activating a version of the same kind of client at the same moment, against a real Postgres.
 *
 * The first holds its transaction open for half a second after inserting its activation, so the second reaches the
 * insert while the first is still uncommitted. The version in force is the one with the greatest activation
 * identity, drawn at the insert; without the lock the second would return first and the first would commit last,
 * answering success for a version that never became the one in force.
 */
class LifetimeVersionsConcurrencyIntegrationSpec :
    StringSpec(
        {
            "an activation waits for the one before it to commit, so the last to return is the one in force" {
                val persistence = PostgresPersistence(PostgresFixture.migrated())
                try {
                    val versions = SessionsStorageFactory().lifetimeVersions()
                    val kept = listOf(2.hours, 3.hours).map { idle ->
                        persistence.transactions.inTransaction {
                            val lifetimes = CheckedLifetimes(ClientType.Browser, idle, 2.days, 5.minutes).lifetimes()
                            Verdict.Commit(versions.add(lifetimes, SampleChange().change(), NOW))
                        }
                    }
                    val firstEnded = AtomicLong()
                    val secondReturned = AtomicLong()

                    coroutineScope {
                        launch {
                            persistence.transactions.inTransaction {
                                versions.activate(kept[0], SampleChange().change(), NOW)
                                delay(500.milliseconds)
                                firstEnded.set(System.nanoTime())
                                Verdict.Commit(Unit)
                            }
                        }
                        launch {
                            delay(100.milliseconds)
                            persistence.transactions.inTransaction {
                                versions.activate(kept[1], SampleChange().change(), NOW)
                                secondReturned.set(System.nanoTime())
                                Verdict.Commit(Unit)
                            }
                        }
                    }

                    (secondReturned.get() >= firstEnded.get()) shouldBe true
                } finally {
                    persistence.close()
                }
            }
        },
    )
