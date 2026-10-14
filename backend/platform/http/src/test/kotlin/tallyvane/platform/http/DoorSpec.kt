package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Surface
import tallyvane.platform.kernel.TransactionRunnerFake
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val PERSON = Uuid.parse("00000000-0000-7000-8000-00000000000a")
private val SESSION = Uuid.parse("00000000-0000-7000-8000-0000000000e1")

private const val ADMIN_HOST = "admin.example.test"
private const val ADMIN_ORIGIN = "https://$ADMIN_HOST"
private const val KEY = "00000000-0000-0000-0000-000000000002"

/**
 * Screens of the administrators' site: served through that door and no other. Counts how often it ran.
 */
private class AdminDesk(private val ran: AtomicInteger) : RouteModule {
    override val basePath: BasePath = BasePath("/desk")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.get("/papers") { call.respondText("papers") }
        route.post("/stamp") {
            ran.incrementAndGet()
            call.respondText("stamped")
        }
    }
}

/**
 * Routes of nobody in particular: either door serves them.
 */
private class Counter : RouteModule {
    override val basePath: BasePath = BasePath("/counter")

    override fun install(route: Route) {
        route.get("/open") { call.respondText("open") }
    }
}

private fun doors(ran: AtomicInteger = AtomicInteger()): Api = Api(
    routes = listOf(AdminDesk(ran), Counter()),
    failures = FailureTranslator.Chained(emptyList()),
    trace = TraceHeader(IdGeneratorFake()),
    ledger = LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-14T09:00:00Z"))),
    callers = Callers { Caller.Signed(PERSON, SESSION) },
    surfaces = Surfaces(APP_ORIGIN, ADMIN_ORIGIN),
)

class DoorSpec :
    StringSpec(
        {
            "routes of the administrators' door are served through it" {
                testApplication {
                    application { doors().install(this) }

                    val answer = client.get("/api/v1/desk/papers") { header(HttpHeaders.Host, ADMIN_HOST) }

                    answer.status shouldBe HttpStatusCode.OK
                }
            }

            "through the console's door they are not there, even for someone signed in" {
                testApplication {
                    application { doors().install(this) }

                    val answer = client.get("/api/v1/desk/papers")

                    answer.status shouldBe HttpStatusCode.NotFound
                }
            }

            "a change asked through the wrong door does not run" {
                val ran = AtomicInteger()
                testApplication {
                    application { doors(ran).install(this) }

                    val answer = client.post("/api/v1/desk/stamp") {
                        header("Idempotency-Key", KEY)
                        fromApp()
                    }

                    answer.status shouldBe HttpStatusCode.NotFound
                    ran.get() shouldBe 0
                }
            }

            "the same change through the right door runs" {
                val ran = AtomicInteger()
                testApplication {
                    application { doors(ran).install(this) }

                    val answer = client.post("/api/v1/desk/stamp") {
                        header("Idempotency-Key", KEY)
                        header(HttpHeaders.Host, ADMIN_HOST)
                        header(HttpHeaders.Origin, ADMIN_ORIGIN)
                    }

                    answer.status shouldBe HttpStatusCode.OK
                    ran.get() shouldBe 1
                }
            }

            "routes that name no door are served through both" {
                testApplication {
                    application { doors().install(this) }

                    client.get("/api/v1/counter/open").status shouldBe HttpStatusCode.OK
                    client.get("/api/v1/counter/open") { header(HttpHeaders.Host, ADMIN_HOST) }.status shouldBe
                        HttpStatusCode.OK
                }
            }
        },
    )
