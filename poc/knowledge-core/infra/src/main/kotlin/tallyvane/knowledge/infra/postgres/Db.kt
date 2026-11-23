package tallyvane.knowledge.infra.postgres

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Where the adapters run their statements: one transaction for each call, off the caller's thread. */
internal class Db(private val database: Database) {
    suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }
}
