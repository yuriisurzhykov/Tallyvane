package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.LifetimeRules
import kotlin.time.Duration.Companion.milliseconds

/**
 * [LifetimeVersions] over the `sessions` schema in Postgres, inside the caller's transaction (ADR-052).
 *
 * One statement reads the version in force for every kind of client: the last activation of each, joined
 * to its version, so a request pays one query however many kinds there are. The rules come back through
 * [LifetimeRules.restore], so what is kept is checked against the bounds the code sets today, and a row
 * that falls outside them fails here and not in a request's judgement of a session.
 */
internal class PostgresLifetimeVersions : LifetimeVersions {
    private val words = StoredDevices()

    override fun active(): LifetimeRules {
        val latest = LifetimeActivationsTable.id.max()
        val inForce = LifetimeActivationsTable.select(latest).groupBy(LifetimeActivationsTable.clientType)
        val rows = LifetimeActivationsTable
            .join(LifetimeVersionsTable, JoinType.INNER) {
                (LifetimeActivationsTable.clientType eq LifetimeVersionsTable.clientType) and
                    (LifetimeActivationsTable.number eq LifetimeVersionsTable.number)
            }
            .select(
                LifetimeVersionsTable.clientType,
                LifetimeVersionsTable.idleMillis,
                LifetimeVersionsTable.absoluteMillis,
            )
            .where { LifetimeActivationsTable.id inSubQuery inForce }
            .toList()
        return LifetimeRules.restore { record ->
            rows.forEach {
                record.lifetimes(
                    words.clientFrom(it[LifetimeVersionsTable.clientType]),
                    it[LifetimeVersionsTable.idleMillis].milliseconds,
                    it[LifetimeVersionsTable.absoluteMillis].milliseconds,
                )
            }
        }
    }

    override fun toString(): String = "PostgresLifetimeVersions(schema=sessions)"
}
