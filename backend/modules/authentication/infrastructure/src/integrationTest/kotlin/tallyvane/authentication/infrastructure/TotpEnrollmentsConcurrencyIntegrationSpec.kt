package tallyvane.authentication.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import tallyvane.authentication.application.AuthenticatorApp
import tallyvane.authentication.domain.CodeVerdict
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ANN = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000a"))
private val ENTROPY = "12345678901234567890".toByteArray(Charsets.US_ASCII)
private val AT = Instant.parse("2026-10-03T09:00:20Z")

/**
 * Two requests that really overlap, against a real Postgres: one code typed twice at once is taken once.
 *
 * Both read the enrolment with [tallyvane.authentication.application.port.TotpEnrollments.lock] and check
 * the same code. The first holds its transaction open for half a second after saving the step it took, so
 * the second's lock meets a row that is held. The second must wait, read the step the first saved, and
 * find the code spent; if it read before, both would accept it, and a code seen over a shoulder would
 * work twice.
 */
class TotpEnrollmentsConcurrencyIntegrationSpec :
    StringSpec(
        {
            "a code typed twice at once is accepted once" {
                val persistence = PostgresPersistence(PostgresFixture.migrated())
                try {
                    val enrollments = storageForTests().totpEnrollments()
                    val key = mutableListOf<String>()
                    TotpEnrollment.begin(ENTROPY).writeTo { seed, _, _ -> key += seed.revealed() }
                    val code = AuthenticatorApp(key.single()).codeAt(AT)
                    val active = TotpEnrollment.restore { record ->
                        TotpEnrollment.begin(ENTROPY).writeTo { seed, _, _ ->
                            record.kept(seed, TotpEnrollment.Standing.Active, 0L)
                        }
                    }
                    persistence.transactions.inTransaction {
                        enrollments.keep(ANN, active)
                        Verdict.Commit(Unit)
                    }

                    suspend fun typeIt(afterwards: Boolean): String = persistence.transactions.inTransaction {
                        val before = checkNotNull(enrollments.lock(ANN))
                        val told = before.check(code, AT).reportTo(
                            object : CodeVerdict.Report<String> {
                                override fun accepted(next: TotpEnrollment): String {
                                    enrollments.keep(ANN, next)
                                    return "accepted"
                                }

                                override fun wrong(): String = "wrong"
                            },
                        )
                        if (afterwards) {
                            delay(500.milliseconds)
                        }
                        Verdict.Commit(told)
                    }

                    val outcomes = coroutineScope {
                        val first = async { typeIt(afterwards = true) }
                        val second = async {
                            delay(100.milliseconds)
                            typeIt(afterwards = false)
                        }
                        first.await() to second.await()
                    }

                    outcomes shouldBe ("accepted" to "wrong")
                } finally {
                    persistence.close()
                }
            }
        },
    )
