package tallyvane.authentication.application

import tallyvane.authentication.domain.SignInPolicy

/**
 * What came of reading a proposal: a policy that may exist, or every fault found.
 */
internal sealed interface Drafted {
    fun <T> reportTo(report: Report<T>): T

    class Passed(private val policy: SignInPolicy) : Drafted {
        override fun <T> reportTo(report: Report<T>): T = report.passed(policy)
    }

    class Refused(private val faults: List<Fault>) : Drafted {
        override fun <T> reportTo(report: Report<T>): T = report.refused(faults)
    }

    interface Report<out T> {
        fun passed(policy: SignInPolicy): T

        fun refused(faults: List<Fault>): T
    }
}
