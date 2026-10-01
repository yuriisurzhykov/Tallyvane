package tallyvane.authentication.application

import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake held to the suite every [tallyvane.authentication.application.port.PolicyVersions] must pass.
 */
class PolicyVersionsFakeSpec : PolicyVersionsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val versions = PolicyVersionsFake()
        override val transactions = TransactionRunnerFake()
    }
}
