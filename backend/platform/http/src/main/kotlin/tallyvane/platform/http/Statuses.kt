package tallyvane.platform.http

import io.ktor.http.HttpStatusCode

/**
 * What a status the framework answered on its own becomes.
 *
 * Ktor answers some requests without any of our code running: an unmatched path, a method the route
 * does not accept, a media type nothing can parse. Those answers are a bare status code and no body
 * at all, which is how the one error format came to hold everywhere except the most ordinary failures
 * an API has.
 *
 * ### Why this is not [Answers]
 *
 * [Answers] is handed to modules, as a receiver inside their own mapping table, and its set of seven
 * meanings is closed on purpose: a module picks a meaning and cannot invent a status. Putting "make a
 * document for status N" there would hand every module exactly the freedom that set exists to
 * withhold.
 *
 * So it is a port of its own, with one caller — the renderer — and one reason to change: what an
 * unadorned status looks like on the wire. That is a different reason from "what a module's failure
 * looks like", which is why the two are not one interface.
 *
 * ### `internal`, which is what lets the implementation nest
 *
 * `Answers` could not nest its implementation: Kotlin has no `internal` members in an interface, so
 * nesting inside a public interface would have made the implementation public and given every module
 * a constructor for a [Problem]. This interface is `internal` itself, so a class nested in it is
 * reachable only where the interface is — inside `platform:http`.
 */
internal interface Statuses {
    public fun problem(status: HttpStatusCode): Problem

    /**
     * The shape RFC 9457 defines for a failure with nothing to add.
     *
     * §4.2.1 registers `about:blank` for exactly this: "the problem has no additional semantics
     * beyond that of the HTTP status code", with the title the status's own recommended phrase — "Not
     * Found" for 404, "Method Not Allowed" for 405. So no list of codes is kept anywhere, and no
     * `type` is invented for a failure that carries no meaning of ours.
     *
     * No `detail`: there is nothing to say that the code has not said, and a detail invented here
     * would be a sentence no module wrote.
     */
    public class AboutBlank : Statuses {
        override fun problem(status: HttpStatusCode): Problem = Problem(
            type = BLANK,
            title = status.description,
            status = status.value,
        )

        private companion object {
            const val BLANK = "about:blank"
        }
    }
}
