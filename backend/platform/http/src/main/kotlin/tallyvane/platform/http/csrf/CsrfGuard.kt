package tallyvane.platform.http.csrf

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import tallyvane.platform.kernel.Secret

/**
 * One interchangeable CSRF defense mechanism, checked against every state-changing request.
 * [ContentTypeGuard], [DoubleSubmitGuard] and [OriginAllowlistGuard] are three independent
 * mechanisms rather than one combined check, because each is defeated a different way and the
 * design's own resource-cost note names why a caller should be free to run more than one:
 * `platform:http/README.md`.
 *
 * None reaches an external technology — each reads only the current [ApplicationCall] — so each
 * nests on this interface rather than living as a top-level `infrastructure` adapter
 * (`ENGINEERING-PRINCIPLES.md`'s "the file publishes one abstraction").
 *
 * Not wired into [tallyvane.platform.http.Api] yet — which guards run, in what combination, and
 * where [DoubleSubmitGuard]'s cookie is actually written are composition-root decisions the
 * design defers to the slice that builds `identity`'s own routes: `platform:http/README.md`.
 */
public interface CsrfGuard {
    /**
     * True if [call] may proceed; false if this guard's own mechanism refuses it. Every
     * implementation answers `true` for a request [SAFE_METHODS] already exempts.
     */
    public fun allows(call: ApplicationCall): Boolean

    /**
     * Rejects a state-changing request whose `Content-Type` is not `application/json`.
     *
     * A cross-site form submission (the classic CSRF vector) can only ever set `Content-Type` to
     * one of the three MIME types HTML forms support — `application/x-www-form-urlencoded`,
     * `multipart/form-data`, `text/plain` — never `application/json`. Sending JSON cross-origin
     * from a script instead of a form requires a CORS preflight, which enforces this server's own
     * origin policy before the real request is ever sent — so a forged form post cannot present
     * this content type, only a same-origin script deliberately choosing to.
     */
    public class ContentTypeGuard : CsrfGuard {
        override fun allows(call: ApplicationCall): Boolean {
            if (call.request.httpMethod !in STATE_CHANGING) return true
            return call.request.contentType().withoutParameters() == JSON
        }

        private companion object {
            val JSON = ContentType.Application.Json.withoutParameters()
        }
    }

    /**
     * Rejects a state-changing request whose [headerName] header does not match its [cookieName]
     * cookie, byte for byte — the double-submit pattern: a cross-site attacker's page can trigger
     * a request that carries the victim's cookie automatically, but same-origin policy stops that
     * page's own script from *reading* the cookie's value to also send as the header, so it cannot
     * produce a match.
     *
     * Only checks the match; issuing [cookieName] on a response is a separate concern this class
     * does not own — see this interface's own KDoc for why that wiring is not built yet.
     *
     * Compares with [Secret] rather than plain `String` equality, the same constant-time
     * comparison every other presented-token check in this codebase already uses
     * (`identity:domain/README.md`'s entry on `HashedToken`) — a smaller risk here than a session
     * token, since same-origin policy is this pattern's real defense, but not zero, and no call
     * site needed to reason about a plain `==` to avoid it.
     */
    public class DoubleSubmitGuard(
        private val cookieName: String = DEFAULT_COOKIE_NAME,
        private val headerName: String = DEFAULT_HEADER_NAME,
    ) : CsrfGuard {
        override fun allows(call: ApplicationCall): Boolean {
            if (call.request.httpMethod !in STATE_CHANGING) return true
            val cookie = call.request.cookies[cookieName]
            val header = call.request.headers[headerName]
            return cookie != null && header != null && Secret(cookie) == Secret(header)
        }

        public companion object {
            public const val DEFAULT_COOKIE_NAME: String = "csrf_token"
            public const val DEFAULT_HEADER_NAME: String = "X-CSRF-Token"
        }
    }

    /**
     * Rejects a state-changing request whose `Origin` header is missing or outside
     * [allowedOrigins].
     *
     * The weakest of the three alone — a browser omits `Origin` on some same-origin requests
     * older clients still send, which this class treats as a refusal rather than guess at intent —
     * and the cheapest, since it reads one header and nothing else. Meant to compose with
     * [ContentTypeGuard] and [DoubleSubmitGuard] through [Composite], not to stand in for either.
     */
    public class OriginAllowlistGuard(private val allowedOrigins: Set<String>) : CsrfGuard {
        override fun allows(call: ApplicationCall): Boolean {
            if (call.request.httpMethod !in STATE_CHANGING) return true
            val origin = call.request.headers[HttpHeaders.Origin]
            return origin != null && origin in allowedOrigins
        }
    }

    /**
     * Every [guards] must allow the call — the shape
     * [tallyvane.platform.http.problems.FailureTranslator.Chained] already uses for combining
     * independent links, applied here to guards instead of failure translators.
     *
     * ```
     * val guards = listOf(CsrfGuard.ContentTypeGuard(), CsrfGuard.OriginAllowlistGuard(allowedOrigins))
     * CsrfGuard.Composite(guards).allows(call) // false the moment any one guard refuses
     * ```
     */
    public class Composite(private val guards: List<CsrfGuard>) : CsrfGuard {
        override fun allows(call: ApplicationCall): Boolean = guards.all { it.allows(call) }
    }

    public companion object {
        /**
         * A request none of these guards needs to examine: it cannot carry a browser's ambient
         * credentials into a state change, so nothing here has anything to defend against.
         */
        public val SAFE_METHODS: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

        private val STATE_CHANGING = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)
    }
}
