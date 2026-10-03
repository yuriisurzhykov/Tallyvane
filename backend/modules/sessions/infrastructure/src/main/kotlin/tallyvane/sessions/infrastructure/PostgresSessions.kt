package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.platform.kernel.Digest
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.DeviceName
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Platform
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val CLAIM_SESSION = """
    insert into sessions.sessions (
        id, secret_digest, pepper_version, account_id, authenticated_at, confirmed_at, last_active_at,
        client_type, device_browser, device_platform, device_mobile, device_name
    )
    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    on conflict (secret_digest) do nothing
    returning id
"""

private const val ADD_FACTOR = """
    insert into sessions.session_factors (session_id, factor)
    values (?, ?)
    on conflict (session_id, factor) do nothing
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
 *
 * ### Confirmation
 *
 * [confirm] moves `confirmed_at` forward only: its `WHERE` holds the rule, as [saw]'s does, so a slow
 * request cannot make a session staler. It reads nothing else from the session it is given but the moment
 * and the factors, and adds those factors to the ones kept; the row is found by the key and never by
 * anything the session says about itself.
 *
 * ### Whose
 *
 * Every act on one session by its id also names the account, in the same `WHERE`: a session of another
 * account matches no row, so the id alone is never enough to reach anyone's session.
 */
internal class PostgresSessions : Sessions {
    private val factors = StoredFactors()
    private val devices = StoredDevices()
    private val restored = RestoredSessions(devices, factors)

    override fun find(key: Digest): Session? {
        val told = DigestColumns().also { key.writeTo(it) }
        // One statement, so one snapshot: a sign-out that commits meanwhile removes the session with its
        // factors or leaves both, and never the factors alone.
        val rows = restored.rows()
            .where { (SessionsTable.secretDigest eq told.bytes()) and (SessionsTable.pepperVersion eq told.version()) }
            .toList()
        return rows.takeIf { it.isNotEmpty() }?.let(restored::from)
    }

    override fun add(key: Digest, session: Session) {
        val told = DigestColumns().also { key.writeTo(it) }
        val rows = Rows(told)
        session.writeTo(rows)
        rows.claim()
        rows.factors().forEach { (id, factor) ->
            SessionFactorsTable.insert {
                it[sessionId] = id.value
                it[SessionFactorsTable.factor] = factors.of(factor)
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

    override fun ofAccount(account: Uuid): List<Session> = restored.rows()
        .where { SessionsTable.accountId eq account }
        .toList()
        .groupBy { it[SessionsTable.id] }
        .values
        .map(restored::from)

    override fun revoke(account: Uuid, id: SessionId): Boolean =
        SessionsTable.deleteWhere { (SessionsTable.id eq id.value) and (accountId eq account) } > 0

    override fun revokeOthers(account: Uuid, keep: SessionId) {
        SessionsTable.deleteWhere { (accountId eq account) and (SessionsTable.id neq keep.value) }
    }

    override fun revokeAll(account: Uuid) {
        SessionsTable.deleteWhere { accountId eq account }
    }

    override fun confirm(key: Digest, session: Session) {
        val told = DigestColumns().also { key.writeTo(it) }
        val proof = Proof().also { session.writeTo(it) }
        val kept = SessionsTable.select(SessionsTable.id)
            .where { (SessionsTable.secretDigest eq told.bytes()) and (SessionsTable.pepperVersion eq told.version()) }
            .singleOrNull()
            ?.get(SessionsTable.id) ?: return
        SessionsTable.update(
            {
                (SessionsTable.id eq kept) and (SessionsTable.confirmedAt less proof.confirmedAt())
            },
        ) {
            it[confirmedAt] = proof.confirmedAt()
        }
        proof.factors().forEach { factor ->
            TransactionManager.current().exec(
                ADD_FACTOR,
                listOf<Pair<IColumnType<*>, Any?>>(
                    SessionFactorsTable.sessionId.columnType to kept,
                    SessionFactorsTable.factor.columnType to factors.of(factor),
                ),
            )
        }
    }

    override fun rename(account: Uuid, id: SessionId, name: DeviceName): Boolean {
        val text = mutableListOf<String>()
        name.writeTo { text += it }
        return SessionsTable.update({ (SessionsTable.id eq id.value) and (SessionsTable.accountId eq account) }) {
            it[deviceName] = text.single()
        } > 0
    }

    override fun toString(): String = "PostgresSessions(schema=sessions)"

    /**
     * Collects what the session tells, to write its row and then the factors for their own table.
     */
    private inner class Rows(private val digest: DigestColumns) : Session.Record {
        private val sessions = mutableListOf<Told>()
        private val devicesTold = mutableListOf<DeviceTold>()
        private val proved = mutableListOf<Factor>()

        override fun session(
            id: SessionId,
            account: Uuid,
            client: ClientType,
            authenticatedAt: Instant,
            confirmedAt: Instant,
            lastActiveAt: Instant,
        ) {
            sessions += Told(id, account, client, authenticatedAt, confirmedAt, lastActiveAt)
        }

        override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) {
            devicesTold += DeviceTold(browser, platform, mobile, name)
        }

        override fun proved(factor: Factor) {
            proved += factor
        }

        fun factors(): List<Pair<SessionId, Factor>> = proved.map { sessions.single().id to it }

        fun claim() {
            val session = sessions.single()
            val device = devicesTold.single()
            val table = SessionsTable
            val claimed = TransactionManager.current().exec(
                CLAIM_SESSION,
                listOf<Pair<IColumnType<*>, Any?>>(
                    table.id.columnType to session.id.value,
                    table.secretDigest.columnType to digest.bytes(),
                    table.pepperVersion.columnType to digest.version(),
                    table.accountId.columnType to session.account,
                    table.authenticatedAt.columnType to session.authenticatedAt,
                    table.confirmedAt.columnType to session.confirmedAt,
                    table.lastActiveAt.columnType to session.lastActiveAt,
                    table.clientType.columnType to devices.of(session.client),
                    table.deviceBrowser.columnType to devices.of(device.browser),
                    table.devicePlatform.columnType to devices.of(device.platform),
                    table.deviceMobile.columnType to device.mobile,
                    table.deviceName.columnType to device.name,
                ),
                // `returning` makes it a query to the driver, whatever word the statement starts with.
                StatementType.SELECT,
            ) { rows -> rows.next() }
            check(claimed == true) {
                "A session is already kept under this key. Two 256-bit secrets do not collide, so look for " +
                    "a generator that repeats itself."
            }
        }
    }

    /**
     * What a confirmed session says about the proof, and nothing else of it.
     */
    private class Proof : Session.Record {
        private var confirmedAt: Instant? = null
        private val proved = mutableListOf<Factor>()

        override fun session(
            id: SessionId,
            account: Uuid,
            client: ClientType,
            authenticatedAt: Instant,
            confirmedAt: Instant,
            lastActiveAt: Instant,
        ) {
            this.confirmedAt = confirmedAt
        }

        override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) = Unit

        override fun proved(factor: Factor) {
            proved += factor
        }

        fun confirmedAt(): Instant = checkNotNull(confirmedAt) { "A session tells when it was last confirmed." }

        fun factors(): List<Factor> = proved
    }

    private class Told(
        val id: SessionId,
        val account: Uuid,
        val client: ClientType,
        val authenticatedAt: Instant,
        val confirmedAt: Instant,
        val lastActiveAt: Instant,
    )

    private class DeviceTold(val browser: Browser, val platform: Platform, val mobile: Boolean, val name: String?)
}
