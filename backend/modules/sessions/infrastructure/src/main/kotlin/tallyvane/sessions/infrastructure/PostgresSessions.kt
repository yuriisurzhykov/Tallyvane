package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.IdGenerator
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Session
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val CLAIM_SESSION = """
    insert into sessions.sessions (id, secret_digest, pepper_version, account_id, authenticated_at, last_active_at)
    values (?, ?, ?, ?, ?, ?)
    on conflict (secret_digest) do nothing
    returning id
"""

/**
 * [Sessions] over the `sessions` schema in Postgres, inside the caller's transaction (ADR-052).
 *
 * ### Last use
 *
 * [saw] is one `UPDATE` whose `WHERE` holds the whole rule: it changes the row only when the last use
 * kept is at least [Session.USE_GRAIN] before the moment noted. A request that does not qualify
 * matches no row, so it takes no row lock and writes nothing, which is what keeps a person who clicks
 * around from writing on every request. Two requests that do qualify at once simply queue on the row.
 */
internal class PostgresSessions(private val ids: IdGenerator) : Sessions {
    private val words = StoredFactors()

    override fun find(key: Digest): Session? {
        val told = DigestColumns().also { key.writeTo(it) }
        val head = SessionsTable.selectAll()
            .where { (SessionsTable.secretDigest eq told.bytes()) and (SessionsTable.pepperVersion eq told.version()) }
            .singleOrNull() ?: return null
        val factors = SessionFactorsTable.selectAll()
            .where { SessionFactorsTable.sessionId eq head[SessionsTable.id] }
            .map { words.from(it[SessionFactorsTable.factor]) }
        return Session.restore { record ->
            record.session(
                head[SessionsTable.accountId],
                head[SessionsTable.authenticatedAt],
                head[SessionsTable.lastActiveAt],
            )
            factors.forEach(record::proved)
        }
    }

    override fun add(key: Digest, session: Session) {
        val told = DigestColumns().also { key.writeTo(it) }
        val id = ids.next()
        val factors = mutableListOf<Factor>()
        session.writeTo(Rows(id, told, factors))
        factors.forEach { factor ->
            SessionFactorsTable.insert {
                it[sessionId] = id
                it[SessionFactorsTable.factor] = words.of(factor)
            }
        }
    }

    override fun forget(key: Digest) {
        val told = DigestColumns().also { key.writeTo(it) }
        SessionsTable.deleteWhere {
            (secretDigest eq told.bytes()) and (pepperVersion eq told.version())
        }
    }

    override fun saw(key: Digest, at: Instant) {
        val told = DigestColumns().also { key.writeTo(it) }
        val notedBefore = at - Session.USE_GRAIN
        SessionsTable.update(
            {
                (SessionsTable.secretDigest eq told.bytes()) and
                    (SessionsTable.pepperVersion eq told.version()) and
                    (SessionsTable.lastActiveAt lessEq notedBefore)
            },
        ) {
            it[lastActiveAt] = at
        }
    }

    override fun toString(): String = "PostgresSessions(schema=sessions)"

    /**
     * Writes the session's row from what the session tells, and collects the factors for their own table.
     */
    private class Rows(
        private val id: Uuid,
        private val digest: DigestColumns,
        private val factors: MutableList<Factor>,
    ) : Session.Record {
        override fun session(account: Uuid, authenticatedAt: Instant, lastActiveAt: Instant) {
            val table = SessionsTable
            val claimed = TransactionManager.current().exec(
                CLAIM_SESSION,
                listOf<Pair<IColumnType<*>, Any?>>(
                    table.id.columnType to id,
                    table.secretDigest.columnType to digest.bytes(),
                    table.pepperVersion.columnType to digest.version(),
                    table.accountId.columnType to account,
                    table.authenticatedAt.columnType to authenticatedAt,
                    table.lastActiveAt.columnType to lastActiveAt,
                ),
                // `returning` makes it a query to the driver, whatever word the statement starts with.
                StatementType.SELECT,
            ) { rows -> rows.next() }
            check(claimed == true) {
                "A session is already kept under this key. Two 256-bit secrets do not collide, so look for " +
                    "a generator that repeats itself."
            }
        }

        override fun proved(factor: Factor) {
            factors += factor
        }
    }
}
