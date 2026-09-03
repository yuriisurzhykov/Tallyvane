package tallyvane.platform.http.csrf

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

/**
 * `/probe`, answering 200 when [guard] allows the call and 403 otherwise, on every method a real
 * route might use — the one route every [CsrfGuard] spec mounts, so each test differs only in the
 * request it sends, not in the server it stands up.
 */
internal fun Application.probeGuardedBy(guard: CsrfGuard) {
    val respondByGuard: suspend RoutingContext.() -> Unit = {
        val status = if (guard.allows(call)) HttpStatusCode.OK else HttpStatusCode.Forbidden
        call.respond(status)
    }
    routing {
        get("/probe", respondByGuard)
        post("/probe", respondByGuard)
        put("/probe", respondByGuard)
        patch("/probe", respondByGuard)
        delete("/probe", respondByGuard)
    }
}
