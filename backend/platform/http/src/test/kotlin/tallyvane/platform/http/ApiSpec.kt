package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.IdGeneratorFake

/**
 * A body with a camelCase property, so the naming strategy has something to rename.
 *
 * A first draft responded with a `Map`, and the test failed for the wrong reason: a naming
 * strategy renames a class's *properties*, and a map's keys are data. The map proved nothing
 * about the configuration.
 */
@Serializable
private data class Payslip(val takeHomeCents: Int)

private sealed interface Refusal : Failure {
    data object Range : Refusal

    data object Owner : Refusal
}

private class Refusals : Problems<Refusal> {
    override fun of(failure: Refusal): Problem = when (failure) {
        is Refusal.Range -> Problem.invalid(FieldError("salary_min_cents", "range.invalid"))
        is Refusal.Owner -> Problem.forbidden("Not yours")
    }
}

/**
 * Routes that exercise each of the four guarantees `Api` claims.
 *
 * `boom` throws an exception carrying a connection string, which is the case §17 cares about:
 * a driver's message is where hosts, ports and credentials live.
 */
private class Probes(private val problems: Refusals) : RouteModule {
    override val basePath: BasePath = BasePath("/probes")

    override fun install(route: Route) {
        route.get("/fine") { call.respond(Payslip(takeHomeCents = 1)) }
        route.get("/refused") { call.respond(problems.of(Refusal.Range)) }
        route.get("/forbidden") { call.respond(problems.of(Refusal.Owner)) }
        route.get("/boom") {
            error("jdbc:postgresql://tallyvane:hunter2@10.0.0.4:5432/db is unreachable")
        }
    }
}

private fun api(): Api = Api(
    routes = listOf(Probes(Refusals())),
    failures = FailureTranslator.Chained(listOf(FailureTranslator.Unrecognised())),
    trace = TraceHeader(IdGeneratorFake()),
)

class ApiSpec :
    StringSpec(
        {
            "a successful answer is snake_case, because §11.1 promises the contract is" {
                testApplication {
                    application { api().install(this) }

                    val body = client.get("/api/v1/probes/fine").bodyAsText()

                    body shouldContain "take_home_cents"
                    body shouldNotContain "takeHomeCents"
                }
            }

            "a Problem sets the status from its own document, not 200" {
                testApplication {
                    application { api().install(this) }

                    val answer = client.get("/api/v1/probes/refused")

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                }
            }

            "a Problem is sent as application/problem+json" {
                testApplication {
                    application { api().install(this) }

                    val answer = client.get("/api/v1/probes/forbidden")

                    answer.headers["Content-Type"]!! shouldContain "application/problem+json"
                    answer.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "a Problem carries the trace id, so a user can quote what is on their screen" {
                testApplication {
                    application { api().install(this) }

                    val body = Json.parseToJsonElement(client.get("/api/v1/probes/refused").bodyAsText())

                    body.jsonObject["trace_id"]!!.jsonPrimitive.content shouldNotBe ""
                }
            }

            "every answer names its trace in a header, including ones with no body" {
                testApplication {
                    application { api().install(this) }

                    val answer = client.get("/api/v1/probes/fine")

                    answer.headers["traceparent"]!! shouldContain "00-"
                }
            }

            // The case the slice exists for: an unmapped failure must not become a leak.
            "an escaped exception becomes 500 and says nothing about the driver" {
                testApplication {
                    application { api().install(this) }

                    val answer = client.get("/api/v1/probes/boom")
                    val body = answer.bodyAsText()

                    answer.status shouldBe HttpStatusCode.InternalServerError
                    body shouldContain "\"status\":500"
                    body shouldNotContain "hunter2"
                    body shouldNotContain "10.0.0.4"
                    body shouldNotContain "postgresql"
                    body shouldNotContain "IllegalStateException"
                    body shouldNotContain "at tallyvane"
                }
            }

            "an incoming traceparent is continued, so one action stays one story" {
                testApplication {
                    application { api().install(this) }
                    val incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

                    val body =
                        Json.parseToJsonElement(
                            client.get("/api/v1/probes/refused") {
                                headers { append("traceparent", incoming) }
                            }.bodyAsText(),
                        )

                    body.jsonObject["trace_id"]!!.jsonPrimitive.content shouldBe
                        "4bf92f3577b34da6a3ce929d0e0e4736"
                }
            }

            "a malformed traceparent is discarded, not the request" {
                testApplication {
                    application { api().install(this) }

                    val answer =
                        client.get("/api/v1/probes/fine") {
                            headers { append("traceparent", "nonsense") }
                        }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.headers["traceparent"]!! shouldNotContain "nonsense"
                }
            }
        },
    )
