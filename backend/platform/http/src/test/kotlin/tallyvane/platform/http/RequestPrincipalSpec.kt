package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * Whether [RequestPrincipal.install] actually runs its resolver before a route sees the call, and
 * whether [RequestPrincipal.of] reads back exactly what it resolved — checked through a real Ktor
 * pipeline via `testApplication`, not by calling the interceptor's lambda by hand.
 */
class RequestPrincipalSpec :
    StringSpec({
        "a route reads back what the resolver returned for the cookie it received" {
            val resolver = object : RequestPrincipalResolver {
                override suspend fun resolve(rawSessionCookie: String?): Any? =
                    rawSessionCookie?.let { "principal-for-$it" }
            }

            testApplication {
                application {
                    RequestPrincipal(resolver).install(this)
                    routing { get("/whoami") { call.respondText(RequestPrincipal.of(call)?.toString() ?: "none") } }
                }

                val response = client.get("/whoami") { cookie("session", "abc123") }

                response.bodyAsText() shouldBe "principal-for-abc123"
            }
        }

        "a request with no session cookie resolves nothing" {
            val resolver = object : RequestPrincipalResolver {
                override suspend fun resolve(rawSessionCookie: String?): Any? = rawSessionCookie?.let { "resolved" }
            }

            testApplication {
                application {
                    RequestPrincipal(resolver).install(this)
                    routing { get("/whoami") { call.respondText(RequestPrincipal.of(call)?.toString() ?: "none") } }
                }

                val response = client.get("/whoami")

                response.bodyAsText() shouldBe "none"
            }
        }

        "off a call the interceptor never touched, of() is null rather than throwing" {
            val resolver = object : RequestPrincipalResolver {
                override suspend fun resolve(rawSessionCookie: String?): Any? = "irrelevant"
            }
            RequestPrincipal(resolver)

            testApplication {
                application {
                    routing { get("/whoami") { call.respondText((RequestPrincipal.of(call) ?: "none").toString()) } }
                }

                client.get("/whoami").bodyAsText() shouldBe "none"
            }
        }
    })
