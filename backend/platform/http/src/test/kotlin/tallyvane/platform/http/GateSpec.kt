package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val PERSON = Uuid.parse("00000000-0000-7000-8000-00000000000a")

private const val KEY = "00000000-0000-0000-0000-000000000001"

/**
 * An address open to anyone, which counts how often it ran so a refusal can be shown to have come first.
 */
private class Lobby(val ran: AtomicInteger) : RouteModule {
    override val basePath: BasePath = BasePath("/lobby")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.get("/hello") { call.respondText("hello") }
        route.post("/enter") {
            ran.incrementAndGet()
            call.respondText("entered")
        }
    }
}

/**
 * An address nobody said anything about, so it is closed. It answers with who asked.
 */
private class Vault : RouteModule {
    override val basePath: BasePath = BasePath("/vault")

    override fun install(route: Route) {
        route.get("/who") {
            call.respondText(Requester(call).caller().reportTo(Naming()))
        }
        route.post("/open") { call.respondText("opened") }
    }
}

/**
 * An address that is a dangerous act: only a person who confirmed recently may reach it. It counts how
 * often it ran, so a refusal can be shown to have come first.
 */
private class Danger(val ran: AtomicInteger) : RouteModule {
    override val basePath: BasePath = BasePath("/danger")

    override val access: Access = Access.SignedFresh

    override fun install(route: Route) {
        route.get("/who") { call.respondText(Requester(call).caller().reportTo(Naming())) }
        route.post("/do") {
            ran.incrementAndGet()
            call.respondText("done")
        }
    }
}

private class Naming : Caller.Report<String> {
    override fun signedIn(account: Uuid): String = "person $account"

    override fun confirmed(account: Uuid): String = "confirmed person $account"

    override fun lapsed(): String = "lapsed"

    override fun anonymous(): String = "anonymous"
}

private fun gated(ran: AtomicInteger = AtomicInteger()): Api = Api(
    routes = listOf(Lobby(ran), Vault(), Danger(ran)),
    failures = FailureTranslator.Chained(emptyList()),
    trace = TraceHeader(IdGeneratorFake()),
    ledger = LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-02T09:00:00Z"))),
    callers = Callers { call ->
        when (call.request.headers["X-Who"]) {
            "person" -> Caller.Signed(PERSON)
            "confirmed" -> Caller.Confirmed(PERSON)
            "lapsed" -> Caller.Lapsed()
            else -> Caller.Anonymous()
        }
    },
    appOrigin = APP_ORIGIN,
)

private suspend fun HttpClient.enter(block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): HttpResponse =
    post("/api/v1/lobby/enter") {
        header("Idempotency-Key", KEY)
        block()
    }

class GateSpec :
    StringSpec(
        {
            "an unsafe request from the application's own origin runs" {
                val ran = AtomicInteger()
                testApplication {
                    application { gated(ran).install(this) }

                    val answer = client.enter { fromApp() }

                    answer.status shouldBe HttpStatusCode.OK
                    ran.get() shouldBe 1
                }
            }

            "an unsafe request from another origin is refused as forbidden, and does not run" {
                val ran = AtomicInteger()
                testApplication {
                    application { gated(ran).install(this) }

                    val answer = client.enter { header(HttpHeaders.Origin, "https://evil.example") }

                    answer.status shouldBe HttpStatusCode.Forbidden
                    ran.get() shouldBe 0
                }
            }

            "a sibling of our origin is another origin: the comparison is exact, not 'same site'" {
                val ran = AtomicInteger()
                testApplication {
                    application { gated(ran).install(this) }

                    val answer = client.enter { header(HttpHeaders.Origin, "https://blog.example.test") }

                    answer.status shouldBe HttpStatusCode.Forbidden
                    ran.get() shouldBe 0
                }
            }

            "an origin that merely begins with ours is another origin" {
                val ran = AtomicInteger()
                testApplication {
                    application { gated(ran).install(this) }

                    val answer = client.enter { header(HttpHeaders.Origin, "$APP_ORIGIN.evil.example") }

                    answer.status shouldBe HttpStatusCode.Forbidden
                    ran.get() shouldBe 0
                }
            }

            "with no Origin, Sec-Fetch-Site same-origin is enough" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.enter { header("Sec-Fetch-Site", "same-origin") }

                    answer.status shouldBe HttpStatusCode.OK
                }
            }

            "with no Origin, a request from the same site but another origin is refused" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.enter { header("Sec-Fetch-Site", "same-site") }

                    answer.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "a request with neither header is refused" {
                testApplication {
                    application { gated().install(this) }

                    client.enter { }.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "a safe request is not asked where it comes from" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.get("/api/v1/lobby/hello") {
                        header(HttpHeaders.Origin, "https://evil.example")
                    }

                    answer.status shouldBe HttpStatusCode.OK
                }
            }

            "the origin is checked before the Idempotency-Key, so a forged request leaves no claim" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.post("/api/v1/lobby/enter") {
                        header(HttpHeaders.Origin, "https://evil.example")
                    }

                    answer.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "a body that is not JSON is refused as an unsupported media type, and does not run" {
                val ran = AtomicInteger()
                testApplication {
                    application { gated(ran).install(this) }

                    val answer = client.enter {
                        fromApp()
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody("a=b")
                    }

                    answer.status shouldBe HttpStatusCode.UnsupportedMediaType
                    ran.get() shouldBe 0
                }
            }

            "JSON with a charset is still JSON" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.enter {
                        fromApp()
                        header(HttpHeaders.ContentType, "application/json; charset=utf-8")
                        setBody("{}")
                    }

                    answer.status shouldBe HttpStatusCode.OK
                }
            }

            "a closed route refuses a request with no credential as sign-in required" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.get("/api/v1/vault/who")

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "sign-in-required"
                }
            }

            "a closed route refuses an ended session as session-expired, which the client tells apart" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.get("/api/v1/vault/who") { header("X-Who", "lapsed") }

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "session-expired"
                }
            }

            "a closed route lets a signed-in person in, and the route learns who" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.get("/api/v1/vault/who") { header("X-Who", "person") }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe "person $PERSON"
                }
            }

            "a dangerous route refuses a person who has not confirmed recently, and says to confirm" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.get("/api/v1/danger/who") { header("X-Who", "person") }

                    answer.status shouldBe HttpStatusCode.Forbidden
                    answer.bodyAsText() shouldContain "step-up-required"
                }
            }

            "a dangerous route lets in a person who confirmed, and the route learns who" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.get("/api/v1/danger/who") { header("X-Who", "confirmed") }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe "confirmed person $PERSON"
                }
            }

            "a dangerous route still tells a stranger to sign in and an ended session to sign in again" {
                testApplication {
                    application { gated().install(this) }

                    val nobody = client.get("/api/v1/danger/who")
                    val lapsed = client.get("/api/v1/danger/who") { header("X-Who", "lapsed") }

                    nobody.bodyAsText() shouldContain "sign-in-required"
                    lapsed.bodyAsText() shouldContain "session-expired"
                }
            }

            "a refused dangerous request does not run, and leaves no claim" {
                val ran = AtomicInteger()
                testApplication {
                    application { gated(ran).install(this) }

                    val answer = client.post("/api/v1/danger/do") {
                        fromApp()
                        header("Idempotency-Key", KEY)
                        header("X-Who", "person")
                    }
                    val confirmed = client.post("/api/v1/danger/do") {
                        fromApp()
                        header("Idempotency-Key", KEY)
                        header("X-Who", "confirmed")
                    }

                    answer.status shouldBe HttpStatusCode.Forbidden
                    confirmed.status shouldBe HttpStatusCode.OK
                    ran.get() shouldBe 1
                }
            }

            "an odd spelling of a path does not reach a dangerous route on a stale proof" {
                testApplication {
                    application { gated().install(this) }

                    val encoded = client.get("/api/v1/lobby/%2e%2e/danger/who") { header("X-Who", "person") }
                    val doubled = client.get("/api/v1/vault//../danger/who") { header("X-Who", "person") }

                    encoded.bodyAsText() shouldContain "step-up-required"
                    doubled.bodyAsText() shouldContain "step-up-required"
                }
            }

            "a person who has not confirmed still reaches an ordinary closed route" {
                testApplication {
                    application { gated().install(this) }

                    client.get("/api/v1/vault/who") { header("X-Who", "person") }.status shouldBe HttpStatusCode.OK
                }
            }

            "a public route is open to a nobody, and knows it is a nobody" {
                testApplication {
                    application { gated().install(this) }

                    client.get("/api/v1/lobby/hello").bodyAsText() shouldBe "hello"
                }
            }

            "being signed in is decided before the Idempotency-Key, so a refused request leaves no claim" {
                testApplication {
                    application { gated().install(this) }

                    val answer = client.post("/api/v1/vault/open") { fromApp() }

                    answer.status shouldBe HttpStatusCode.Unauthorized
                }
            }

            "an address nobody mounted under the API is closed too" {
                testApplication {
                    application { gated().install(this) }

                    client.get("/api/v1/nowhere").status shouldBe HttpStatusCode.Unauthorized
                }
            }

            "a path that reaches a closed route by way of a public one does not borrow its openness" {
                testApplication {
                    application { gated().install(this) }

                    val encoded = client.get("/api/v1/lobby/%2e%2e/vault/who")
                    val doubled = client.get("/api/v1/lobby//../vault/who")

                    encoded.status shouldBe HttpStatusCode.Unauthorized
                    doubled.status shouldBe HttpStatusCode.Unauthorized
                }
            }

            "outside the API nothing is decided here" {
                testApplication {
                    application { gated().install(this) }

                    client.get("/elsewhere").status shouldBe HttpStatusCode.NotFound
                }
            }
        },
    )
