package tallyvane.sessions.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.fromApp
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.application.Harness

private class Probe : RouteModule {
    override val basePath: BasePath = BasePath("/probe")

    override fun install(route: Route) {
        route.get { call.respondText("in") }
    }
}

private fun HttpResponse.setCookies(): List<String> = headers.getAll(HttpHeaders.SetCookie).orEmpty()

private suspend fun ApplicationTestBuilder.exchange(attempt: Secret?) = client.post("/api/v1/sessions") {
    fromApp()
    header("Idempotency-Key", "0199a000-0000-7000-8000-000000000001")
    attempt?.let { withCookie("__Host-attempt", it) }
}

private suspend fun ApplicationTestBuilder.probe(session: Secret?) = client.get("/api/v1/probe") {
    session?.let { withCookie("__Host-session", it) }
}

private const val ADMIN_HOST = "admin.example.test"

private suspend fun ApplicationTestBuilder.exchangeAsAdmin(attempt: Secret?) = client.post("/api/v1/sessions") {
    header(HttpHeaders.Host, ADMIN_HOST)
    header(HttpHeaders.Origin, "https://$ADMIN_HOST")
    header("Idempotency-Key", "0199a000-0000-7000-8000-000000000011")
    attempt?.let { withCookie("__Host-attempt", it) }
}

private suspend fun ApplicationTestBuilder.probeAsAdmin(session: Secret?) = client.get("/api/v1/probe") {
    header(HttpHeaders.Host, ADMIN_HOST)
    session?.let { withCookie("__Host-session", it) }
}

private fun HttpResponse.sessionSecret(): Secret =
    Secret(setCookies().single { it.startsWith("__Host-session=") }.substringAfter("=").substringBefore(";"))

class SessionRoutesSpec :
    StringSpec(
        {
            "exchanges a completed sign-in for a session cookie, and takes the attempt cookie away" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }

                    val answer = exchange(served.harness.finishedSigningIn())

                    answer.status shouldBe HttpStatusCode.NoContent
                    val cookies = answer.setCookies()
                    cookies shouldHaveSize 2
                    val session = cookies.single { it.startsWith("__Host-session=") }
                    session shouldContain "Secure"
                    session shouldContain "HttpOnly"
                    session shouldContain "Path=/"
                    session shouldContain "SameSite=Lax"
                    session shouldContain "Max-Age=7776000"
                    session shouldContain "secret-1"
                    cookies.single { it.startsWith("__Host-attempt=") } shouldContain "Max-Age=0"
                }
            }

            "gives a session that lets the person through to a closed route" {
                testApplication {
                    val served = Served()
                    application { served.api(listOf(Probe())).install(this) }
                    val answer = exchange(served.harness.finishedSigningIn())
                    val secret =
                        Secret(
                            answer.setCookies().single {
                                it.startsWith("__Host-session=")
                            }.substringAfter("=").substringBefore(";"),
                        )

                    probe(secret).status shouldBe HttpStatusCode.OK
                }
            }

            "refuses a browser with no completed sign-in, as a problem" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }

                    val answer = exchange(null)

                    answer.status shouldBe HttpStatusCode.NotFound
                    answer.headers[HttpHeaders.ContentType] shouldStartWith "application/problem+json"
                    answer.setCookies() shouldHaveSize 0
                }
            }

            "does not exchange the same sign-in twice" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val attempt = served.harness.finishedSigningIn()
                    exchange(attempt).status shouldBe HttpStatusCode.NoContent

                    client.post("/api/v1/sessions") {
                        fromApp()
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000002")
                        withCookie("__Host-attempt", attempt)
                    }.status shouldBe HttpStatusCode.NotFound
                }
            }

            "closes a route to a request with no session, and to one with a session nobody issued" {
                testApplication {
                    val served = Served()
                    application { served.api(listOf(Probe())).install(this) }

                    probe(null).status shouldBe HttpStatusCode.Unauthorized
                    val forged = probe(Secret("never-issued"))

                    forged.status shouldBe HttpStatusCode.Unauthorized
                    forged.headers[HttpHeaders.ContentType] shouldStartWith "application/problem+json"
                }
            }

            "signing out ends the session, answers 204 and clears the cookie" {
                testApplication {
                    val served = Served()
                    application { served.api(listOf(Probe())).install(this) }
                    val secret = served.harness.signedIn()
                    probe(secret).status shouldBe HttpStatusCode.OK

                    val answer = client.delete("/api/v1/session") {
                        fromApp()
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000003")
                        withCookie("__Host-session", secret)
                    }

                    answer.status shouldBe HttpStatusCode.NoContent
                    answer.setCookies().single() shouldContain "Max-Age=0"
                    probe(secret).status shouldBe HttpStatusCode.Unauthorized
                }
            }

            "signing out with no session is still 204" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }

                    client.delete("/api/v1/session") {
                        fromApp()
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000004")
                    }.status shouldBe HttpStatusCode.NoContent
                }
            }

            "refuses a sign-out that does not come from the application's own page" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val secret = served.harness.signedIn()

                    client.delete("/api/v1/session") {
                        header(HttpHeaders.Origin, "https://evil.example.test")
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000005")
                        withCookie("__Host-session", secret)
                    }.status shouldBe HttpStatusCode.Forbidden

                    served.harness.who(secret).shouldStartWith("signed in")
                }
            }

            "exchanges an administrator's completed sign-in on the administrators' host for a session good only there" {
                testApplication {
                    val served = Served()
                    served.harness.admins.grant(Harness.ACCOUNT)
                    application { served.api(listOf(Probe())).install(this) }

                    val answer = exchangeAsAdmin(served.harness.finishedSigningInAsAdmin())

                    answer.status shouldBe HttpStatusCode.NoContent
                    val secret = answer.sessionSecret()
                    probeAsAdmin(secret).status shouldBe HttpStatusCode.OK
                    probe(secret).status shouldBe HttpStatusCode.Unauthorized
                }
            }

            "a console session carried to the administrators' host is not good there" {
                testApplication {
                    val served = Served()
                    application { served.api(listOf(Probe())).install(this) }
                    val secret = served.harness.signedIn()

                    probeAsAdmin(secret).status shouldBe HttpStatusCode.Unauthorized
                    probe(secret).status shouldBe HttpStatusCode.OK
                }
            }

            "refuses a person who is not an administrator on the administrators' host, with no cookie" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }

                    val answer = exchangeAsAdmin(served.harness.finishedSigningInAsAdmin())

                    answer.status shouldBe HttpStatusCode.Forbidden
                    answer.headers[HttpHeaders.ContentType] shouldStartWith "application/problem+json"
                    answer.setCookies() shouldHaveSize 0
                }
            }

            "an administrator's sign-in is not taken on the console's host" {
                testApplication {
                    val served = Served()
                    served.harness.admins.grant(Harness.ACCOUNT)
                    application { served.api().install(this) }

                    exchange(served.harness.finishedSigningInAsAdmin()).status shouldBe HttpStatusCode.NotFound
                }
            }

            "refuses the console's origin on the administrators' host" {
                testApplication {
                    val served = Served()
                    served.harness.admins.grant(Harness.ACCOUNT)
                    application { served.api().install(this) }

                    client.post("/api/v1/sessions") {
                        header(HttpHeaders.Host, ADMIN_HOST)
                        fromApp()
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000012")
                        withCookie("__Host-attempt", served.harness.finishedSigningInAsAdmin())
                    }.status shouldBe HttpStatusCode.Forbidden
                }
            }
        },
    )
