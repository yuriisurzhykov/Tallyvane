package tallyvane.knowledge.infra

import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

/** A database made for one test, empty, with the pgvector extension available, and the means to connect to it. */
class TestDatabase(val url: String, val user: String, val password: String, private val admin: Admin) {
    /** Runs [sql] on this database over a plain JDBC connection, and returns the first column of each row. */
    fun query(sql: String): List<String> =
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    fun count(table: String): Int = query("select count(*) from $table").single().toInt()

    fun drop() = admin.drop(url.substringAfterLast('/'))

    class Admin(private val url: String, private val user: String, private val password: String) {
        fun create(name: String) = run("create database $name")

        fun drop(name: String) = run("drop database if exists $name with (force)")

        fun urlOf(name: String): String = url.substringBeforeLast('/') + "/" + name

        fun credentials(): Pair<String, String> = user to password

        private fun run(sql: String) {
            DriverManager.getConnection(url, user, password).use { it.createStatement().use { statement -> statement.execute(sql) } }
        }
    }

    companion object {
        private val counter = AtomicInteger()

        /**
         * A new database on the Postgres named by `POC_TEST_DATABASE_URL` (with `POC_TEST_DATABASE_USER` and
         * `POC_TEST_DATABASE_PASSWORD`), for a machine without Docker; otherwise on a container of the image
         * that `docker-compose.yml` runs. Fails, and does not skip, when neither is there (ADR-057).
         */
        fun fresh(): TestDatabase {
            val admin = serverAdmin()
            val name = "poc_${System.nanoTime()}_${counter.incrementAndGet()}"
            admin.create(name)
            val (user, password) = admin.credentials()
            return TestDatabase(admin.urlOf(name), user, password, admin)
        }

        private fun serverAdmin(): Admin {
            System.getenv("POC_TEST_DATABASE_URL")?.let { url ->
                return Admin(url, System.getenv("POC_TEST_DATABASE_USER") ?: "postgres", System.getenv("POC_TEST_DATABASE_PASSWORD") ?: "")
            }
            return container
        }

        private val container: Admin by lazy {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is not available. Start it, or set POC_TEST_DATABASE_URL to a Postgres with pgvector installed."
            }
            val postgres = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            postgres.start()
            Admin(postgres.jdbcUrl, postgres.username, postgres.password)
        }
    }
}
