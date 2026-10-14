package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.Step
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Everything the use cases that administer policies need, as fakes, with the policies of ADR-078 in force as version 1
 * of each purpose, made by nobody in particular.
 *
 * Starts with two administrators, [ann] and [bob], and with [cal], who has an account and no right to administer.
 */
class PolicyDesk {
    val ann = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000a"))
    val bob = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000b"))
    val cal = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000c"))

    val admins = AdminsFake().also {
        it.grant(ann)
        it.grant(bob)
    }
    val accounts = AccountsFake()
    val secondFactors = SecondFactorsFake()
    val versions = PolicyVersionsFake()
    val clock = Harness.TickingClock(Instant.parse("2026-10-14T09:00:00Z"))
    val transactions = TransactionRunnerFake()

    private val words = PolicyWords()
    private val reach =
        Reach(admins, accounts, Enrollments(secondFactors, secondFactors), EnrollmentCensusFake(secondFactors))

    init {
        Purpose.entries.forEach { purpose ->
            val change = SampleChange().change()
            versions.activate(versions.add(CheckedPolicy(purpose).policy(), change, clock.now()), change, clock.now())
        }
    }

    val list: ListPolicyVersionsUseCase =
        ListPolicyVersionsUseCase.ListPolicyVersions(admins, versions, words, transactions)

    val add: AddPolicyVersionUseCase =
        AddPolicyVersionUseCase.AddPolicyVersion(admins, versions, words, transactions, clock)

    val preview: PreviewPolicyVersionUseCase =
        PreviewPolicyVersionUseCase.PreviewPolicyVersion(admins, versions, reach, words, transactions)

    val activate: ActivatePolicyVersionUseCase =
        ActivatePolicyVersionUseCase.ActivatePolicyVersion(admins, versions, reach, words, transactions, clock)

    /**
     * Gives [account] an active TOTP and a set of recovery codes, as a person who turned it on has.
     */
    fun withTotp(account: AccountId) {
        secondFactors.keep(account, activeAt(1))
        secondFactors.keep(account, RecoveryCodes.issue(listOf(digest(1))))
    }

    /**
     * Leaves [account] with recovery codes only: its seed was retired and the codes are not used up.
     */
    fun withRecoveryCodesOnly(account: AccountId) {
        secondFactors.keep(account, activeAt(1).retired())
        secondFactors.keep(account, RecoveryCodes.issue(listOf(digest(1))))
    }

    /**
     * Passes [time] on the clock, so that what is kept next carries a later instant.
     */
    fun after(time: Duration) {
        clock.passes(time)
    }

    /**
     * The proposal for a policy with the limits of ADR-078, which a case changes one thing in.
     */
    fun proposal(
        purpose: String,
        steps: List<ProposedStep>,
        attemptLifetimeMillis: Long = FIVE_MINUTES,
        maxFailures: Int = 5,
        firstDelayMillis: Long = ONE_SECOND,
    ): ProposedPolicy = ProposedPolicy(purpose, steps, attemptLifetimeMillis, maxFailures, firstDelayMillis)

    /**
     * What a case reads off the outcome of a listing: the versions as lines, newest first.
     */
    fun listed(outcome: PolicyVersionsListed): List<String> {
        val listener = HistoryLines()
        (outcome as PolicyVersionsListed.Listed).writeTo(listener)
        return listener.told()
    }

    /**
     * What a case reads off the outcome of a preview: people reached, and people who could not pass.
     */
    fun previewed(outcome: PolicyVersionPreviewed): Pair<Int, Int> {
        val told = mutableListOf<Pair<Int, Int>>()
        (outcome as PolicyVersionPreviewed.Previewed).writeTo { people, cannotPass -> told += people to cannotPass }
        return told.single()
    }

    /**
     * The faults an outcome tells, as `field code` pairs.
     */
    fun faults(write: (Fault.Record) -> Unit): List<String> {
        val told = mutableListOf<String>()
        write(Fault.Record { field, code, _ -> told += "$field $code" })
        return told
    }

    private class HistoryLines : KeptVersions.Record {
        private val story = mutableListOf<String>()

        fun told(): List<String> = story.toList()

        override fun kept(madeBy: AccountId?, comment: String?, madeAt: Instant) {
            story += "kept by ${madeBy?.value} because '$comment'"
        }

        override fun inForce(since: Instant, by: AccountId?, reason: String?) {
            story += "in force since $since by ${by?.value} because '$reason'"
        }

        override fun number(number: Int) {
            story += "number $number"
        }

        override fun purpose(purpose: Purpose) = Unit

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) = Unit
    }

    private companion object {
        const val FIVE_MINUTES = 300_000L
        const val ONE_SECOND = 1_000L
    }
}
