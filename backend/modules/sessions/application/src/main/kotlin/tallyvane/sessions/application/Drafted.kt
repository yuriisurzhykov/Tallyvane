package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientLifetimes

/**
 * What came of reading a proposal: lifetimes that may exist, or every fault found.
 */
internal sealed interface Drafted {
    fun <T> reportTo(report: Report<T>): T

    class Passed(private val lifetimes: ClientLifetimes) : Drafted {
        override fun <T> reportTo(report: Report<T>): T = report.passed(lifetimes)
    }

    class Refused(private val faults: List<Fault>) : Drafted {
        override fun <T> reportTo(report: Report<T>): T = report.refused(faults)
    }

    interface Report<out T> {
        fun passed(lifetimes: ClientLifetimes): T

        fun refused(faults: List<Fault>): T
    }
}
