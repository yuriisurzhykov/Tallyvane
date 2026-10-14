package tallyvane.authentication.application

import tallyvane.authentication.domain.DraftCheck
import tallyvane.authentication.domain.PolicyDraft
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import tallyvane.authentication.domain.Step
import tallyvane.authentication.domain.Violation
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads a [ProposedPolicy] into a draft and checks it, saying everything wrong with it at once.
 *
 * First the words: a purpose, kind or necessity the API has no word for is a fault of its own, named by the field
 * that held it. Then, if every word means something, the draft is checked against the bounds the code sets and each
 * bound it breaks is a fault too. A policy comes out only when there is no fault.
 *
 * One instance reads one proposal.
 */
internal class Drafting(private val words: PolicyWords) : ProposedPolicy.Record {
    private var purpose: Purpose? = null
    private var attemptLifetimeMillis = 0L
    private var maxFailures = 0
    private var firstDelayMillis = 0L
    private var position = 0
    private val steps = mutableListOf<Step>()
    private val faults = mutableListOf<Fault>()

    /**
     * The policy [proposal] describes, or every reason there is none.
     */
    fun of(proposal: ProposedPolicy): Drafted {
        proposal.writeTo(this)
        val named = purpose
        return if (faults.isEmpty() && named != null) checked(named) else Drafted.Refused(faults.toList())
    }

    private fun checked(named: Purpose): Drafted = PolicyDraft(
        named,
        steps.toList(),
        attemptLifetimeMillis.milliseconds,
        maxFailures,
        firstDelayMillis.milliseconds,
    ).check().reportTo(Checked())

    override fun policy(purpose: String, attemptLifetimeMillis: Long, maxFailures: Int, firstDelayMillis: Long) {
        this.purpose = words.purposeNamed(purpose)
        if (this.purpose == null) {
            faults += words.unknownPurpose(purpose)
        }
        this.attemptLifetimeMillis = attemptLifetimeMillis
        this.maxFailures = maxFailures
        this.firstDelayMillis = firstDelayMillis
    }

    override fun step(accepts: List<String>, necessity: String) {
        val kinds = accepts.associateWith { word -> words.kindNamed(word) }
        val applies = words.necessityNamed(necessity)
        kinds.filterValues { it == null }.keys.forEach { word ->
            faults += Fault("steps[$position].accepts", "unknown", "There is no factor called '$word'.")
        }
        if (applies == null) {
            faults += Fault(
                "steps[$position].necessity",
                "unknown",
                "A step applies as 'always' or 'when_enrolled', not '$necessity'.",
            )
        }
        steps += Step(kinds.values.filterNotNull().toSet(), applies ?: Step.Necessity.Always)
        position += 1
    }

    /**
     * Turns what the draft check found into a [Drafted].
     */
    private inner class Checked : DraftCheck.Report<Drafted> {
        override fun passed(policy: SignInPolicy): Drafted = Drafted.Passed(policy)

        override fun rejected(violations: List<Violation>): Drafted =
            Drafted.Refused(violations.map { it.reportTo(PolicyFaults(words)) })
    }
}
