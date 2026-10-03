package tallyvane.platform.http

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import kotlin.uuid.Uuid

internal val CALLER = AttributeKey<Caller>("tallyvane.caller")

/**
 * Who a request comes from, as `Api` found out before any route ran.
 *
 * Asked of the call a route is handling: `Requester(call)`.
 */
public class Requester(private val call: ApplicationCall) {
    /**
     * Who this request comes from. A route behind [Access.Signed] or [Access.SignedFresh] can rely on it being a [Caller.Signed] or a [Caller.Confirmed].
     */
    public fun caller(): Caller = call.attributes[CALLER]

    /**
     * The account of the person this request comes from, for a route that is not [Access.Public].
     *
     * `Api` lets only a signed-in person through to such a route, so this is the answer to "who?" and not
     * a question that can fail. Asking it of a public route, where the request may come from nobody, is a
     * mistake in that route, and says so.
     *
     * @throws IllegalStateException when the request does not come from a signed-in person.
     */
    public fun account(): Uuid = caller().reportTo(Requiring())

    override fun toString(): String = "Requester"

    private class Requiring : Caller.Report<Uuid> {
        override fun signedIn(account: Uuid): Uuid = account

        override fun confirmed(account: Uuid): Uuid = account

        override fun lapsed(): Uuid = error(MISTAKE)

        override fun anonymous(): Uuid = error(MISTAKE)

        private companion object {
            const val MISTAKE =
                "This route asked who is signed in, and nobody is. A route that can be reached by a stranger " +
                    "must say so with Access.Public and then not ask; every other route is closed to them."
        }
    }
}
