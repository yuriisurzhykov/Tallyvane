package tallyvane.platform.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import tallyvane.platform.kernel.TransactionRunner
import kotlin.time.Duration.Companion.seconds

/**
 * How many connections a pool holds unless told otherwise — the one place this number is written.
 *
 * Eight comes from the memory budget in `ops/README.md`. It is a default rather than a constant
 * inside the class so that a deploy can override it and nothing else has to name a number.
 */
public const val DEFAULT_SIZE: Int = 8

/**
 * [Persistence] over a JDBC connection pool and Exposed.
 *
 * One object owns the connection pool, the Exposed database and the dispatcher that
 * bounds blocking work, because those three have to agree on a number: the
 * dispatcher's parallelism is the pool's size, so database work can never occupy
 * more threads than there are connections to occupy them with. Splitting them would
 * let the two drift.
 *
 * This is the only name in the module that says "Hikari and Exposed", and only the
 * composition root is supposed to say it. [AutoCloseable] sits here rather than on
 * [Persistence] for the same reason: whoever built the pool closes it, and a consumer
 * that merely runs transactions must not be able to shut it down.
 *
 * ### Constructing one does not require a reachable database
 *
 * The constructor never acquires a connection, so it succeeds against a database that is
 * down and the first failure surfaces from the first transaction instead. Two consequences
 * for a caller: a process can start and report itself unready rather than not existing, and
 * a pool that has been built recovers on its own once the database answers again — without
 * a restart.
 *
 * @param access where the database is; says nothing about who started it, so the
 * same factory serves a container in tests and a compose service in production.
 * @param size how many connections to hold, and therefore also the parallelism of the
 * dispatcher blocking work runs on — the two are one number by construction. Defaults to
 * [DEFAULT_SIZE], which the deploy overrides; a caller that has no opinion should not have
 * to invent one.
 */
public class PostgresPersistence(access: DatabaseAccess, private val size: Int = DEFAULT_SIZE) :
    Persistence,
    AutoCloseable {
    private val pool: HikariDataSource = HikariDataSource(configuration(access))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val blocking = Dispatchers.IO.limitedParallelism(size)

    private val database =
        Database.connect(
            datasource = pool,
            databaseConfig =
            DatabaseConfig {
                // Exposed re-runs the whole block on SQLException by default. A
                // block here decides and then writes, and re-running it would
                // repeat whatever else it did. A retry that is wanted is written
                // where a reader can see it is a retry.
                defaultMaxAttempts = 1
                defaultQueryTimeout = QUERY_TIMEOUT_SECONDS
            },
        )

    override val transactions: TransactionRunner = ExposedTransactionRunner(database, blocking)

    override fun close() {
        pool.close()
    }

    private fun configuration(access: DatabaseAccess): HikariConfig = HikariConfig().apply {
        jdbcUrl = access.url
        username = access.user
        password = access.password.revealed()
        maximumPoolSize = size
        initializationFailTimeout = DEFER_FIRST_FAILURE
        connectionTimeout = CONNECTION_TIMEOUT_MILLIS
        validationTimeout = VALIDATION_TIMEOUT_MILLIS
        keepaliveTime = KEEPALIVE_MILLIS
        maxLifetime = MAX_LIFETIME_MILLIS
        DriverProperties(this).apply {
            // Seconds, not millis, and strings rather than Ints — see DriverProperties
            // for why an Int here is accepted and then ignored.
            set("connectTimeout", CONNECT_TIMEOUT_SECONDS)
            set("socketTimeout", SOCKET_TIMEOUT_SECONDS)
            set("options", serverBounds.asConnectionOption())
        }
    }

    private companion object {
        /**
         * The bounds PostgreSQL enforces itself, layered under the client-side ones.
         *
         * `statement_timeout` sits above [QUERY_TIMEOUT_SECONDS] so the cheaper client cancel
         * usually wins and this is the backstop for anything not issued through Exposed, and
         * below [SOCKET_TIMEOUT_SECONDS] so a statement is cancelled before the socket is torn
         * down — a cancelled statement leaves a usable connection, a dead socket does not.
         *
         * `lock_timeout` is short because a request that has queued this long for a lock has
         * already missed §1.5's p95 target, and while it queues it holds a connection.
         *
         * `idle_in_transaction_session_timeout` catches the leak that holds both a connection and
         * vacuum hostage; `playground/ddl-locks/` shows what one idle transaction does to a
         * concurrent index build.
         */
        val serverBounds = SessionTimeouts(
            statement = 15.seconds,
            lock = 3.seconds,
            idleInTransaction = 60.seconds,
        )

        /**
         * Negative means Hikari builds the pool without acquiring a connection and defers the
         * first failure to the first `getConnection`.
         */
        const val DEFER_FIRST_FAILURE = -1L

        /**
         * Waiting for a free connection. Chooses "slow" over "failed" under a burst.
         */
        const val CONNECTION_TIMEOUT_MILLIS = 2_000L

        const val VALIDATION_TIMEOUT_MILLIS = 1_000L

        /**
         * Finds a connection the tunnel dropped before a request does.
         */
        const val KEEPALIVE_MILLIS = 120_000L

        const val MAX_LIFETIME_MILLIS = 1_800_000L

        const val CONNECT_TIMEOUT_SECONDS = 5

        /**
         * Backstop for a dead socket; must exceed the slowest legitimate statement.
         */
        const val SOCKET_TIMEOUT_SECONDS = 30

        /**
         * Bound on one statement, below [SOCKET_TIMEOUT_SECONDS] so the cause is nameable.
         */
        const val QUERY_TIMEOUT_SECONDS = 10
    }
}
