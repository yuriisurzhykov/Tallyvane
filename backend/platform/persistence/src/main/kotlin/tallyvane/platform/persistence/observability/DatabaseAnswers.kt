package tallyvane.platform.persistence.observability

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import tallyvane.platform.observability.health.HealthCheck

/**
 * Whether this application can reach its database — through the pool it actually uses.
 *
 * ### Why through the pool rather than a connection of its own
 *
 * A connection of its own would answer a different question: is the *server* up. The two
 * diverge exactly when it matters. A saturated pool is the failure that stops this
 * application working, and a probe holding its own private connection would report `up`
 * throughout it. The divergence grows with §12's plan to extract modules into services:
 * several services sharing one server would all report the same fact about the server and
 * none about their own access.
 *
 * The cost is deliberate: a saturated pool makes readiness false, and the orchestrator takes
 * the instance out of rotation. An instance that cannot obtain a connection should not be
 * receiving traffic. `connectionTimeout` (2 s) bounds how long that verdict takes.
 *
 * ### Why it issues a statement
 *
 * Opening a transaction and doing nothing would be a weaker check than it looks: it would
 * pass or fail on whether Exposed acquires its connection eagerly, which is a library
 * detail, not a property of the database. `select 1` needs a connection and a round trip
 * either way.
 *
 * The verdict is [tallyvane.platform.kernel.Verdict.Rollback] because a probe must leave nothing behind. Nothing was
 * written, so the two verdicts have the same effect here — the choice states the intent for
 * the next reader rather than changing behaviour.
 *
 * ### Failures are not caught here
 *
 * A refused connection throws, and this class lets it. `HealthCheck.Contained` turns a
 * throw into `Down` with the exception's type and nothing else (ADR-054), so catching here
 * would either duplicate that or leak a driver message carrying hosts and ports.
 */
public class DatabaseAnswers(private val transactions: TransactionRunner) : HealthCheck {
    override val name: String = NAME

    override val requiredForReadiness: Boolean = true

    override suspend fun check(): Health = transactions.inTransaction {
        Verdict.Rollback(verdictOn(answered()))
    }

    /**
     * `TransactionManager.current()` rather than a receiver, because [TransactionRunner]'s
     * block deliberately takes none: the port is about boundaries, and naming Exposed in its
     * signature would put a driver in `platform:kernel`.
     */
    private fun answered(): Boolean = TransactionManager.Companion.current().exec(PROBE) { rows ->
        rows.next() && rows.getInt(1) == 1
    } ?: false

    private fun verdictOn(answered: Boolean): Health = if (answered) {
        Health.Up
    } else {
        Health.Down(Ailment.Refused("the database accepted $PROBE and returned nothing"))
    }

    private companion object {
        const val NAME = "database"

        const val PROBE = "select 1"
    }
}
