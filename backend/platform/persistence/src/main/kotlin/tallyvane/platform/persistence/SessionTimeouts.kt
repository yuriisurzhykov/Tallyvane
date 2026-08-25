package tallyvane.platform.persistence

import kotlin.time.Duration

/**
 * PostgreSQL's own timeouts, rendered as the `options` connection parameter.
 *
 * These are server settings, so the server enforces them whether or not the client is in a
 * position to react — which is the whole reason they exist here. A coroutine timeout cannot
 * interrupt a blocking JDBC call, and the client-side bounds pgjdbc and Exposed offer are
 * narrower than they look: measured in `playground/timeout-bounds/`, an insert blocked behind a
 * conflicting uncommitted insert was cut off by Exposed's `defaultQueryTimeout` only when it went
 * through Exposed. The same statement sent as plain JDBC on the same pool was still waiting when
 * the spike gave up. `statement_timeout` bounded both.
 *
 * Carried on the connection rather than set on the role or the database, because a test database
 * is made with `CREATE DATABASE … TEMPLATE` and a database-level setting does not survive that —
 * measured while removing `citext`, and recorded in ADR-059. A setting the tests cannot see is a
 * setting the tests cannot check.
 *
 * Every value is optional because the two callers want different sets, and the difference is not
 * cosmetic: a migration is *supposed* to hold a long transaction, so giving it a
 * `statement_timeout` or an `idle_in_transaction_session_timeout` would cancel legitimate work
 * halfway through. It gets [lock] and nothing else.
 */
internal class SessionTimeouts(
    private val statement: Duration? = null,
    private val lock: Duration? = null,
    private val idleInTransaction: Duration? = null,
) {
    init {
        require(statement != null || lock != null || idleInTransaction != null) {
            "SessionTimeouts with nothing set renders an empty option, which reads as a bound and is not one"
        }
    }

    /**
     * The value for pgjdbc's `options` property: `-c statement_timeout=15000ms -c lock_timeout=…`.
     */
    fun asConnectionOption(): String = listOfNotNull(
        statement?.let { setting("statement_timeout", it) },
        lock?.let { setting("lock_timeout", it) },
        idleInTransaction?.let { setting("idle_in_transaction_session_timeout", it) },
    ).joinToString(" ")

    private fun setting(name: String, value: Duration): String = "-c $name=${value.inWholeMilliseconds}ms"
}
