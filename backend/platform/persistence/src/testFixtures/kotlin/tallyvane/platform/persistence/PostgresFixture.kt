package tallyvane.platform.persistence

import org.testcontainers.containers.PostgreSQLContainer

/**
 * One Postgres for the test JVM that asks for it.
 *
 * The image is the one §16.4's compose file runs, `postgres:17-alpine`, because a
 * different build sorts text differently: musl and glibc disagree on collation,
 * and a test that passed on one could fail on the other for no reason a reader
 * would find.
 *
 * Started on first use and never stopped: Testcontainers' reaper removes it when
 * the JVM exits. One container per test JVM, which means per Gradle test task —
 * not one per build. A shared build service would give that, and is the
 * escalation if the container count ever costs more than the code would.
 *
 * Fails rather than skips when Docker is absent. Integration tests only run when
 * asked for, so an absent Docker means the request could not be honoured, and a
 * silent pass would be the worst of the three outcomes.
 */
public object PostgresFixture {
    private const val IMAGE = "postgres:17-alpine"

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer<Nothing>(IMAGE).apply { start() }
    }

    /**
     * Connection details of the running container, starting it if needed.
     */
    public fun access(): DatabaseAccess = DatabaseAccess(
        url = container.jdbcUrl,
        user = container.username,
        password = container.password,
    )
}
