package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.authentication.application.GoogleHandshake
import tallyvane.authentication.application.port.GoogleHandshakes
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret

/**
 * [GoogleHandshakes] over the `authentication` schema in Postgres.
 *
 * Runs inside the caller's transaction (ADR-052). [take] deletes the row it reads, and the delete is
 * what makes a handshake single-use: of two requests racing to take it, the second blocks on the first's
 * row lock and finds the row gone.
 */
internal class PostgresGoogleHandshakes(private val rows: AttemptRows) : GoogleHandshakes {
    override fun keep(attempt: Digest, handshake: GoogleHandshake) {
        val id = checkNotNull(rows.idOf(attempt)) {
            "A handshake is kept for an attempt that is kept first; none is kept under this key."
        }
        handshake.writeTo { state, nonce, verifier ->
            GoogleHandshakesTable.insert {
                it[attemptId] = id
                it[GoogleHandshakesTable.state] = state.revealed()
                it[GoogleHandshakesTable.nonce] = nonce.revealed()
                it[GoogleHandshakesTable.verifier] = verifier.revealed()
            }
        }
    }

    override fun take(attempt: Digest): GoogleHandshake? {
        val id = rows.idOf(attempt)
        val row = id?.let {
            GoogleHandshakesTable.selectAll().where { GoogleHandshakesTable.attemptId eq it }.forUpdate().singleOrNull()
        }
        return row?.let {
            GoogleHandshakesTable.deleteWhere { attemptId eq checkNotNull(id) }
            GoogleHandshake(
                Secret(it[GoogleHandshakesTable.state]),
                Secret(it[GoogleHandshakesTable.nonce]),
                Secret(it[GoogleHandshakesTable.verifier]),
            )
        }
    }

    override fun toString(): String = "PostgresGoogleHandshakes(schema=authentication)"
}
