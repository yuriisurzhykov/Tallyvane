package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.EnrollmentCensus
import tallyvane.authentication.domain.Enrollment
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.SpendVerdict
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

private val CAL = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000c"))

private val TOTP = Enrollment(setOf(FactorKind.Totp))
private val CODES = Enrollment(setOf(FactorKind.RecoveryCode))
private val BOTH = Enrollment(setOf(FactorKind.Totp, FactorKind.RecoveryCode))

/**
 * The behaviour every [EnrollmentCensus] must show, whatever keeps the factors: it counts what an account is said to
 * have set up by [Enrollment.of], so a preview of a policy and a sign-in under it agree about who can pass.
 */
abstract class EnrollmentCensusConformance : StringSpec() {
    /**
     * The census, and the ports through which the factors it counts are kept, over one store.
     */
    interface Subject {
        val storage: SecondFactorStorage
        val census: EnrollmentCensus
    }

    /**
     * A store with no factor set up by anybody.
     */
    protected abstract suspend fun fresh(): Subject

    private suspend fun Subject.counted(): Map<Enrollment, Int> =
        storage.transactions.inTransaction { Verdict.Commit(census.enrolled()) }

    private suspend fun Subject.withTotp(account: AccountId, step: Long = 1) =
        storage.inOwnTransaction { storage.enrollments.keep(account, activeAt(step)) }

    private suspend fun Subject.withCodes(account: AccountId, issued: Int, spent: Int = 0) {
        val codes = (1..issued).map(::digest)
        val set = codes.take(spent).fold(RecoveryCodes.issue(codes)) { held, digest ->
            held.spend(digest, AT).reportTo(
                object : SpendVerdict.Report<RecoveryCodes> {
                    override fun spent(next: RecoveryCodes): RecoveryCodes = next

                    override fun unknown(): RecoveryCodes = error("The code should have been there to spend.")
                },
            )
        }
        storage.inOwnTransaction { storage.codes.keep(account, set) }
    }

    init {
        "counts nobody when nobody has set anything up" {
            fresh().counted() shouldBe emptyMap()
        }

        "counts each account under the combination of factors it has set up" {
            val subject = fresh()
            subject.withTotp(ANN)
            subject.withCodes(ANN, issued = 10)
            subject.withTotp(BOB)
            subject.withTotp(CAL)
            subject.withCodes(CAL, issued = 3)

            subject.counted() shouldBe mapOf(BOTH to 2, TOTP to 1)
        }

        "counts an account whose seed was retired, but whose codes are not all spent, as having the codes only" {
            val subject = fresh()
            subject.storage.inOwnTransaction {
                subject.storage.enrollments.keep(ANN, activeAt(1).retired())
            }
            subject.withCodes(ANN, issued = 3, spent = 1)

            subject.counted() shouldBe mapOf(CODES to 1)
        }

        "does not count an account whose codes are all spent and whose seed is retired" {
            val subject = fresh()
            subject.storage.inOwnTransaction {
                subject.storage.enrollments.keep(ANN, activeAt(1).retired())
            }
            subject.withCodes(ANN, issued = 2, spent = 2)

            subject.counted() shouldBe emptyMap()
        }

        "does not count a TOTP enrolment that was begun and never confirmed" {
            val subject = fresh()
            subject.storage.inOwnTransaction {
                subject.storage.enrollments.keep(ANN, TotpEnrollment.begin(ENTROPY))
            }

            subject.counted() shouldBe emptyMap()
        }
    }
}
