package tallyvane.platform.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.platform.idempotency.Claim
import tallyvane.platform.idempotency.ClaimBusy
import tallyvane.platform.idempotency.ClaimedElsewhere
import tallyvane.platform.idempotency.Earlier
import tallyvane.platform.idempotency.claimed
import tallyvane.platform.kernel.Verdict
import java.sql.DriverManager
import kotlin.time.Duration.Companion.milliseconds

private object Effects : Table("effects") {
    val n = integer("n")
}

/**
 * Two requests with one key at the same moment, against the real index.
 *
 * The conformance suite proves what a claim does one request at a time. What it cannot show is the
 * part ADR-086 rests on: that the database makes the second wait, that it then decides by what the
 * first did, and that the wait is bounded. Each case below holds the first request open on purpose,
 * because a race the test merely hopes for would pass on the day nothing raced.
 */
class PostgresClaimsConcurrencyIntegrationSpec :
    StringSpec(
        {
            "makes a second copy wait for the first, then refuses it once the first committed" {
                val rig = Rig()
                rig.use {
                    val persistence = rig.persistence
                    val release = CompletableDeferred<Unit>()
                    val holding = CompletableDeferred<Unit>()

                    val first = async(Dispatchers.Default) {
                        run(persistence, claimed(1)) {
                            effect()
                            holding.complete(Unit)
                            release.await()
                            Verdict.Commit(Unit)
                        }
                    }
                    holding.await()
                    val second = async(Dispatchers.Default) {
                        runCatching { run(persistence, claimed(1)) { effect().let { Verdict.Commit(Unit) } } }
                    }

                    delay(WAITING)
                    second.isCompleted shouldBe false
                    release.complete(Unit)
                    first.await()

                    second.await().exceptionOrNull().shouldBeInstanceOf<ClaimedElsewhere>()
                    rig.effects() shouldBe 1
                    persistence.ledger.earlier(claimed(1)) shouldBe Earlier.Unanswered
                }
            }

            "lets the second copy run when the first rolled back" {
                val rig = Rig()
                rig.use {
                    val persistence = rig.persistence
                    val release = CompletableDeferred<Unit>()
                    val holding = CompletableDeferred<Unit>()

                    val first = async(Dispatchers.Default) {
                        run(persistence, claimed(1)) {
                            effect()
                            holding.complete(Unit)
                            release.await()
                            Verdict.Rollback(Unit)
                        }
                    }
                    holding.await()
                    val second = async(Dispatchers.Default) {
                        run(persistence, claimed(1)) { effect().let { Verdict.Commit(Unit) } }
                    }

                    delay(WAITING)
                    second.isCompleted shouldBe false
                    release.complete(Unit)
                    first.await()
                    second.await()

                    rig.effects() shouldBe 1
                }
            }

            "gives up on a copy that waited past the lock wait, and leaves the first untouched" {
                val rig = Rig()
                rig.use {
                    val persistence = rig.persistence
                    val release = CompletableDeferred<Unit>()
                    val holding = CompletableDeferred<Unit>()

                    val first = async(Dispatchers.Default) {
                        run(persistence, claimed(1)) {
                            effect()
                            holding.complete(Unit)
                            release.await()
                            Verdict.Commit(Unit)
                        }
                    }
                    holding.await()

                    shouldThrow<ClaimBusy> {
                        run(persistence, claimed(1)) { effect().let { Verdict.Commit(Unit) } }
                    }

                    release.complete(Unit)
                    first.await()
                    rig.effects() shouldBe 1
                }
            }

            "lets exactly one of many simultaneous copies do the work" {
                val rig = Rig()
                rig.use {
                    val persistence = rig.persistence
                    val results = (1..COPIES).map {
                        async(Dispatchers.Default) {
                            runCatching { run(persistence, claimed(1)) { effect().let { Verdict.Commit(Unit) } } }
                        }
                    }.awaitAll()

                    rig.effects() shouldBe 1
                    results.count { it.isSuccess } shouldBe 1
                    results.mapNotNull { it.exceptionOrNull() }.forEach { failure ->
                        (failure is ClaimedElsewhere || failure is ClaimBusy) shouldBe true
                    }
                }
            }
        },
    )

private const val COPIES = 6

private val WAITING = 400.milliseconds

/**
 * A database with one table the work writes to, a pool over it, and a count of what survived taken
 * over a connection of its own.
 */
private class Rig : AutoCloseable {
    private val access = PostgresFixture.migrated()

    init {
        DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
            connection.createStatement().use { it.execute("create table effects (n integer not null)") }
        }
    }

    val persistence = PostgresPersistence(access)

    fun effects(): Int =
        DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from effects").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    override fun close() = persistence.close()
}

private suspend fun <T> run(persistence: PostgresPersistence, claim: Claim, block: suspend () -> Verdict<T>): T =
    withContext(claim) { persistence.transactions.inTransaction(block) }

private fun effect() {
    Effects.insert { it[n] = 1 }
}
