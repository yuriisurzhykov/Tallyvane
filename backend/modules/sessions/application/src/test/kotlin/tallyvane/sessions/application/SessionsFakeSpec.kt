package tallyvane.sessions.application

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.sessions.application.port.Sessions

/**
 * The fake, judged by the suite the adapter over Postgres passes too (ADR-046).
 */
class SessionsFakeSpec : SessionsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val sessions: Sessions = SessionsFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
