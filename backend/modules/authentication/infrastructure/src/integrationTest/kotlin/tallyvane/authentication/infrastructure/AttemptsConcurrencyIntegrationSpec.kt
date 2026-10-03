package tallyvane.authentication.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.application.AttemptStory
import tallyvane.authentication.application.KeysForTests
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.VerifiedFactor
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val ID = KeysForTests().of("concurrent")
private val START = Instant.parse("2026-10-01T09:00:00Z")

/**
 * Two requests that really overlap, against a real Postgres: what the conformance suite can only
 * describe in sequence.
 *
 * Both load the same attempt and each adds a wrong answer, which is two guesses of a code sent
 * together. The first holds its transaction open for half a second after saving, so the second's
 * save meets a row that is locked and not yet committed. The second must wait, read the first's
 * guess when the lock frees, and be told it is out of date; if it did not, one of the two guesses
 * would be missing from the record that limits guessing.
 */
class AttemptsConcurrencyIntegrationSpec :
    StringSpec(
        {
            "two overlapping requests cannot both record the next wrong answer" {
                val persistence = PostgresPersistence(PostgresFixture.migrated())
                try {
                    val attempts = storageForTests().attempts()
                    val loaded = Attempt(
                        Purpose.Login,
                        START,
                    ).withVerified(VerifiedFactor.identifying(FactorKind.Google, "subject-1", START))
                    persistence.transactions.inTransaction { Verdict.Commit(attempts.save(ID, loaded)) }

                    val outcomes = coroutineScope {
                        val first = async {
                            persistence.transactions.inTransaction {
                                val before = checkNotNull(attempts.find(ID))
                                val outcome = attempts.save(ID, before.withFailure(START + 10.seconds))
                                delay(500.milliseconds)
                                Verdict.Commit(outcome)
                            }
                        }
                        val second = async {
                            delay(100.milliseconds)
                            persistence.transactions.inTransaction {
                                val before = checkNotNull(attempts.find(ID))
                                Verdict.Commit(attempts.save(ID, before.withFailure(START + 11.seconds)))
                            }
                        }
                        first.await() to second.await()
                    }

                    outcomes shouldBe (AttemptSaveOutcome.Saved to AttemptSaveOutcome.Superseded)
                    val kept = persistence.transactions.inTransaction { Verdict.Commit(attempts.find(ID)) }
                    AttemptStory(checkNotNull(kept)) shouldBe
                        AttemptStory(loaded.withFailure(START + 10.seconds))
                } finally {
                    persistence.close()
                }
            }
        },
    )
