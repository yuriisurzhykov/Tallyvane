package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.observability.log.TraceContext

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
    override fun Answers.of(failure: Refusal): Problem = when (failure) {
        is Refusal.Range -> invalid(listOf(FieldError("salary_min_cents", "range.invalid")))
        is Refusal.Owner -> forbidden("Not yours")
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
        // The only way to answer with a failure: the failure and the table that maps it. There is
        // no expression here that produces a `Problem` — the route cannot make one.
        route.get("/refused") { call.respond(Refused(Refusal.Range, problems)) }
        route.get("/forbidden") { call.respond(Refused(Refusal.Owner, problems)) }
        route.get("/boom") {
            error("jdbc:postgresql://tallyvane:hunter2@10.0.0.4:5432/db is unreachable")
        }
        route.get("/traced") {
            call.respond(Payslip(takeHomeCents = if (TraceContext.current() == null) 0 else 1))
        }
        route.post("/body") { call.respond(call.receive<Payslip>()) }
    }
}

/**
 * No links of its own: `Api` supplies the framework's translator and the detail-free tail itself,
 * which is what the unknown-path and malformed-body cases below check.
 */
private fun api(): Api = Api(
    routes = listOf(Probes(Refusals())),
    failures = FailureTranslator.Chained(emptyList()),
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

            // The trace has to be in the coroutine context of the handler itself, not merely on
            // the response: that is what makes a log line written inside a use case carry the id.
            "a handler runs inside the trace, so its own log lines carry the id" {
                testApplication {
                    application { api().install(this) }

                    val body = client.get("/api/v1/probes/traced").bodyAsText()

                    body shouldContain "\"take_home_cents\":1"
                }
            }

            // The failure a live run exposed: an exception unwinds past the context element, so the
            // 500 arrived with no trace id in its body and an empty MDC on its log line — no id
            // exactly where a user would quote one.
            "a 500 carries the same trace id as its header, so the log line can be found" {
                testApplication {
                    application { api().install(this) }

                    val answer = client.get("/api/v1/probes/boom")
                    val body = Json.parseToJsonElement(answer.bodyAsText()).jsonObject

                    val inBody = body["trace_id"]!!.jsonPrimitive.content
                    answer.headers["traceparent"]!! shouldContain inBody
                }
            }

            "a valid snake_case body is read, not only written" {
                testApplication {
                    application { api().install(this) }

                    val answer =
                        client.post("/api/v1/probes/body") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"take_home_cents":7}""")
                        }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldContain "\"take_home_cents\":7"
                }
            }

            "an unknown path answers in the error shape, like everything else" {
                testApplication {
                    application { api().install(this) }

                    val answer = client.get("/api/v1/nowhere")

                    answer.status shouldBe HttpStatusCode.NotFound
                    answer.headers["Content-Type"]!! shouldContain "application/problem+json"
                }
            }

            "a malformed body is the client's fault: 400, not 500" {
                testApplication {
                    application { api().install(this) }

                    val answer =
                        client.post("/api/v1/probes/body") {
                            contentType(ContentType.Application.Json)
                            setBody("{ this is not json")
                        }

                    answer.status shouldBe HttpStatusCode.BadRequest
                    answer.headers["Content-Type"]!! shouldContain "application/problem+json"
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
