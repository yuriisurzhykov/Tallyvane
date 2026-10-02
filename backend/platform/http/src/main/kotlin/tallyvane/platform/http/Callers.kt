package tallyvane.platform.http

import io.ktor.server.application.ApplicationCall

/**
 * Finds out who a request comes from, from whatever credential it carries.
 *
 * `Api` asks this once per request, before the idempotency claim is made and before any route runs,
 * and keeps the answer on the call (see [Requester]). The module that issues credentials supplies the
 * implementation, so `platform:http` knows no cookie, no token and no session: replacing it replaces
 * how a person is recognised and nothing else.
 */
public fun interface Callers {
    public suspend fun of(call: ApplicationCall): Caller

    /**
     * Everyone is anonymous. For a test of something that does not depend on who asks.
     */
    public class Anonymous : Callers {
        override suspend fun of(call: ApplicationCall): Caller = Caller.Anonymous()

        override fun toString(): String = "Callers.Anonymous"
    }
}
