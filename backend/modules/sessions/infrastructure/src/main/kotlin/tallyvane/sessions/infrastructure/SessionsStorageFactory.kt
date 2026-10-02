package tallyvane.sessions.infrastructure

import tallyvane.platform.kernel.IdGenerator
import tallyvane.sessions.application.port.Sessions

/**
 * Hands out what `sessions` keeps sessions in, as the port its application layer speaks to (§4.3).
 *
 * The adapter runs inside a transaction the caller opened, so it holds no connection and building it
 * needs no database.
 */
public class SessionsStorageFactory(private val ids: IdGenerator) {
    /**
     * Where sessions are kept.
     */
    public fun sessions(): Sessions = PostgresSessions(ids)

    override fun toString(): String = "SessionsStorageFactory(schema=sessions)"
}
