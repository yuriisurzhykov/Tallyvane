package tallyvane.knowledge.infra.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tallyvane.knowledge.infra.StartupFailure
import java.io.Closeable
import javax.sql.DataSource

/**
 * A connection to the database, migrated. The dimension of the embeddings is part of the schema, so a database
 * made for one embedding model refuses to start with another: the vectors would not fit, and nothing would say so
 * until the first answer.
 */
class Postgres private constructor(private val pool: HikariDataSource, private val database: Database) : Closeable {
    fun knowledge(): PostgresKnowledge = PostgresKnowledge(database)

    internal fun seeds(): PostgresSeeds = PostgresSeeds(Db(database))

    override fun close() = pool.close()

    companion object {
        fun open(url: String, user: String, password: String, embeddingDimensions: Int): Postgres {
            val pool =
                try {
                    HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = url
                            username = user
                            this.password = password
                            maximumPoolSize = POOL_SIZE
                        },
                    )
                } catch (e: PoolInitializationException) {
                    throw StartupFailure("Postgres could not be reached at $url as $user: ${e.cause?.message ?: e.message}", e)
                }
            try {
                migrate(pool, embeddingDimensions)
                val database = Database.connect(pool)
                checkDimensions(database, embeddingDimensions)
                return Postgres(pool, database)
            } catch (e: Exception) {
                pool.close()
                throw e
            }
        }

        private fun migrate(source: DataSource, embeddingDimensions: Int) {
            try {
                Flyway.configure()
                    .dataSource(source)
                    .placeholders(mapOf("embed_dim" to embeddingDimensions.toString()))
                    .load()
                    .migrate()
            } catch (e: org.flywaydb.core.api.FlywayException) {
                throw StartupFailure("The database could not be migrated: ${e.message}", e)
            }
        }

        private fun checkDimensions(database: Database, expected: Int) {
            val actual = transaction(database) {
                exec("select atttypmod from pg_attribute where attrelid = 'action'::regclass and attname = 'embedding'") {
                    if (it.next()) it.getInt(1) else null
                }
            }
            if (actual != expected) {
                throw StartupFailure(
                    "The database was made for embeddings of $actual dimensions and EMBED_DIM is $expected. " +
                        "Use the model the database was made for, or start a fresh database (docker compose down -v).",
                )
            }
        }

        private const val POOL_SIZE = 5
    }
}
