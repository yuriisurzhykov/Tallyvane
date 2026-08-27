package tallyvane.platform.persistence

import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import tallyvane.platform.kernel.Secret
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * One Postgres for the test JVM that asks for it, and a database of its own for every
 * caller.
 *
 * The image must be the one `ops/docker-compose.yml` runs, because a different build sorts
 * text differently: musl and glibc disagree on collation, and a test that passed on one
 * could fail on the other for no reason a reader would find.
 *
 * That agreement is currently unguarded — the tag and the connection limit are written here
 * and in the compose file, and nothing compares them. `backend/.plans/` carries it as an
 * open debt.
 *
 * Started on first use and never stopped: Testcontainers' reaper removes it when the JVM
 * exits. One container per test JVM, which means per Gradle test task — not one per
 * build. A shared build service would give that, and is the escalation if the container
 * count ever costs more than the code would.
 *
 * Fails rather than skips when Docker is absent. Integration tests only run when asked
 * for, so an absent Docker means the request could not be honoured, and a silent pass
 * would be the worst of the three outcomes.
 *
 * ### Why a database per caller, rather than one shared one
 *
 * Sharing a database makes every spec depend on every other spec's leftovers, and the
 * escape from that is truncation — which needs a list of tables that a table added later
 * can be left out of. That is the same "second place to forget" ADR-051 rejected for
 * migration locations.
 *
 * It is not hypothetical here: `FlywayMigrationsSpec` sets `search_path` **on the
 * database**, so on a shared one it would silently change the environment every other
 * spec runs in.
 *
 * [migrated] clones a template that had the migrations applied once, because `create
 * database … template …` copies files rather than replaying migrations — fast enough to
 * do per test, where replaying a growing migration set would not be.
 */
public object PostgresFixture {
    private const val IMAGE = "postgres:17-alpine"

    /**
     * The limit `ops/docker-compose.yml` runs, so a test cannot pass on room production
     * does not have.
     *
     * A connection is a backend process on the server, and one pool holds its full size open
     * at rest — eight, with no query issued, measured in `playground/pool-occupancy`. On the
     * default 100 a spec could hold seven pools and never notice; on 20 the third exhausts
     * the server, which is what made `ExposedTransactionRunnerSpec` close per case instead of
     * per spec.
     *
     * `shared_buffers` and `work_mem` are deliberately *not* mirrored: they change how fast a
     * query runs, never whether it succeeds, and they will diverge anyway when production
     * moves off a 2 GB VPS. This number is the one that decides outcomes.
     */
    private const val MAX_CONNECTIONS = 20

    private const val PORT = 5432

    private const val TEMPLATE = "tallyvane_template"

    private val sequence = AtomicInteger()

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer<Nothing>(IMAGE)
            .apply { withCommand("postgres", "-c", "max_connections=$MAX_CONNECTIONS") }
            .apply { start() }
    }

    /**
     * A new database with nothing in it. For tests about migrating itself.
     */
    public fun empty(): DatabaseAccess = accessTo(created(from = null))

    /**
     * A new database with every migration applied, cloned from a template built once.
     */
    public fun migrated(): DatabaseAccess = accessTo(created(from = template))

    /**
     * Runs [block] with the database frozen, then thaws it — for the one question that cannot be
     * asked of a healthy database: what an application does while its database stops answering,
     * and whether it recovers without being restarted.
     *
     * Pause rather than stop, because stopping the container releases its mapped port and the next
     * start would hand out a different one, so the application under test would be pointed at a
     * door that no longer exists. A paused container keeps the port and simply stops answering.
     *
     * A capability, not the container: callers still cannot reach the container itself (ADR-057),
     * because the moment they can, one of them configures it.
     *
     * **This freezes the server for every database in it.** Kotest runs specs sequentially by
     * default, which is what makes that acceptable; a spec that opted into concurrency alongside
     * this one would see failures it did not cause.
     */
    public fun <T> frozen(block: () -> T): T {
        val docker = DockerClientFactory.instance().client()
        docker.pauseContainerCmd(container.containerId).exec()
        return try {
            block()
        } finally {
            docker.unpauseContainerCmd(container.containerId).exec()
        }
    }

    /**
     * Built once per JVM, then left without a connection: Postgres refuses to clone a
     * template while anyone is attached to it, and Flyway closes its own connections
     * when `migrate` returns.
     */
    private val template: String by lazy {
        val name = TEMPLATE
        create(name, from = null)
        FlywayMigrations(accessTo(name)).apply()
        name
    }

    private fun created(from: String?): String {
        val name = "spec_${sequence.incrementAndGet()}"
        create(name, from)
        return name
    }

    /**
     * Issued over the container's own database, because `create database` cannot be run
     * from inside the database it creates, nor inside a transaction.
     */
    private fun create(name: String, from: String?) {
        val clause = from?.let { " template $it" } ?: ""
        DriverManager
            .getConnection(container.jdbcUrl, container.username, container.password)
            .use { connection ->
                connection.createStatement().use { it.execute("create database $name$clause") }
            }
    }

    private fun accessTo(database: String): DatabaseAccess = DatabaseAccess(
        url = "jdbc:postgresql://${container.host}:${container.getMappedPort(PORT)}/$database",
        user = container.username,
        password = Secret(container.password),
    )
}
