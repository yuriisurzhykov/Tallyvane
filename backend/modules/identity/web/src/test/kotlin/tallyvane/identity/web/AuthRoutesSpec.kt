package tallyvane.identity.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.Api
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.kernel.IdGenerator

class AuthRoutesSpec : StringSpec({
    "auth mutations require same-origin JSON and the issued CSRF token" {
        testApplication {
            application {
                val handler = object : AuthHandler {
                    override fun install(route: Route) {
                        route.post("/probe") { call.respond(HttpStatusCode.NoContent) }
                    }
                }
                Api(
                    listOf(AuthRoutes(listOf(handler), false)),
                    FailureTranslator.Chained(emptyList()), TraceHeader(IdGenerator.Uuid7()),
                    IdentityRoutesFactory().csrf(setOf("https://app.example.com")),
                ).install(this)
            }
            client.post("/api/v1/auth/probe").status shouldBe HttpStatusCode.Forbidden
            val csrf = client.get("/api/v1/auth/csrf")
            csrf.status shouldBe HttpStatusCode.OK
            val cookie = csrf.headers.getAll(HttpHeaders.SetCookie)!!.first().substringBefore(';')
            val token = cookie.substringAfter('=')
            client.post("/api/v1/auth/probe") {
                header(HttpHeaders.Cookie, cookie)
                header("X-CSRF-Token", token)
                header(HttpHeaders.Origin, "https://app.example.com")
                header(HttpHeaders.ContentType, "application/json")
                setBody("{}")
            }.status shouldBe HttpStatusCode.NoContent
            client.post("/api/v1/auth/probe") {
                header(HttpHeaders.Cookie, cookie)
                header("X-CSRF-Token", token)
                header(HttpHeaders.Origin, "https://attacker.example.com")
                header(HttpHeaders.ContentType, "application/json")
                setBody("{}")
            }.status shouldBe HttpStatusCode.Forbidden
        }
    }
})
