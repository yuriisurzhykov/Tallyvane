package tallyvane.sessions.application

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.sessions.application.port.LifetimeVersions
import kotlin.time.Duration

/**
 * The fake, judged by the suite the adapter over Postgres passes too (ADR-046).
 */
class LifetimeVersionsFakeSpec : LifetimeVersionsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = LifetimeVersionsFake()

        override val versions: LifetimeVersions = fake
        override val transactions: TransactionRunner = TransactionRunnerFake()

        override suspend fun activate(idle: Duration, absolute: Duration) = fake.activate(idle, absolute)
    }
}
