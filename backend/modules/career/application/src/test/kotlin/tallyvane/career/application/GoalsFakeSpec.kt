package tallyvane.career.application

import tallyvane.career.application.port.Goals
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

class GoalsFakeSpec : GoalsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val goals: Goals = GoalsFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
