package tallyvane.platform.health

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.Api
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import tallyvane.platform.observability.health.HealthReport
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SECRET = "let-me-in-please"

/**
 * Degraded but ready: the model is down and not required, the database is up. This is the case the
 * three states exist for, and the one where a naive implementation answers 503 and takes a working
 * application out of service.
 */
private fun degradedButReady(): HealthReport = HealthReport(
    status = Health.Degraded(Ailment.Dependencies(listOf("llm"))),
    ready = true,
    checks = listOf(
        HealthReport.Checked("database", Health.Up, 3.milliseconds),
        HealthReport.Checked("llm", Health.Down(Ailment.Overran(2.seconds)), 2.seconds),
    ),
)

private fun notReady(): HealthReport = HealthReport(
    status = Health.Down(Ailment.Dependencies(listOf("database"))),
    ready = false,
    checks = listOf(
        HealthReport.Checked("database", Health.Down(Ailment.Threw("SQLException")), 12.milliseconds),
    ),
)

private fun served(report: HealthReport, reporter: HealthReporterFake = HealthReporterFake(report)): Api = Api(
    routes = listOf(HealthRoutes(reporter, ServiceToken(SECRET))),
    failures = FailureTranslator.Chained(emptyList()),
    trace = TraceHeader(IdGeneratorFake()),
)

class HealthRoutesSpec :
    StringSpec(
        {
            "an unauthorised aggregate answers with one field and names no dependency" {
                testApplication {
                    application { served(degradedButReady()).install(this) }

                    val body = client.get("/api/v1/health").bodyAsText()

                    body shouldBe """{"status":"degraded"}"""
                    // The assertion that matters: not "the shape is right" but "the parts of the
                    // system are not listed". ADR-055 withholds them as reconnaissance.
                    body shouldNotContain "database"
                    body shouldNotContain "llm"
                    body shouldNotContain "ready"
                }
            }

            "an authorised aggregate carries the breakdown, with kind as the discriminator" {
                testApplication {
                    application { served(degradedButReady()).install(this) }

                    val body =
                        client.get("/api/v1/health") {
                            headers { append("X-Service-Token", SECRET) }
                        }.bodyAsText()

                    body shouldContain """"ready":true"""
                    body shouldContain """"name":"database""""
                    body shouldContain """"took_ms":3"""
                    body shouldContain """"kind":"overran""""
                    body shouldContain """"bound_ms":2000"""
                }
            }

            "a wrong token gets the unauthorised answer, not an error" {
                testApplication {
                    application { served(degradedButReady()).install(this) }

                    val answer =
                        client.get("/api/v1/health") {
                            headers { append("X-Service-Token", "wrong-but-same-length") }
                        }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"status":"degraded"}"""
                }
            }

            // `Dependencies` and `Behind` name what the system is made of, so they have no wire form
            // at all — not even for an authorised reader (ADR-055).
            "the aggregate's own cause is never rendered, even authorised" {
                testApplication {
                    application { served(notReady()).install(this) }

                    val body =
                        client.get("/api/v1/health") {
                            headers { append("X-Service-Token", SECRET) }
                        }.bodyAsText()

                    body shouldContain """"status":"down""""
                    body shouldNotContain """"kind":"dependencies""""
                }
            }

            "readiness answers 200 while degraded, because degraded is not unready" {
                testApplication {
                    application { served(degradedButReady()).install(this) }

                    client.get("/api/v1/health/ready").status shouldBe HttpStatusCode.OK
                }
            }

            "readiness answers 503 when a required dependency is down" {
                testApplication {
                    application { served(notReady()).install(this) }

                    client.get("/api/v1/health/ready").status shouldBe HttpStatusCode.ServiceUnavailable
                }
            }

            "readiness says nothing beyond the status, even to an unauthorised caller" {
                testApplication {
                    application { served(notReady()).install(this) }

                    client.get("/api/v1/health/ready").bodyAsText() shouldBe """{"status":"down"}"""
                }
            }

            // The case this slice exists to get right: a liveness probe that consults a dependency
            // turns that dependency's outage into a restart loop of a healthy process.
            "liveness never asks the reporter, so a dead database cannot restart a live process" {
                val reporter = HealthReporterFake(notReady())
                testApplication {
                    application { served(notReady(), reporter).install(this) }

                    val answer = client.get("/api/v1/health/live")

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"status":"up"}"""
                    reporter.asked shouldBe 0
                }
            }

            "every probe forbids caching, because a cached 'up' outlives the truth" {
                testApplication {
                    application { served(degradedButReady()).install(this) }

                    listOf("/api/v1/health", "/api/v1/health/live", "/api/v1/health/ready").forEach { path ->
                        client.get(path).headers["Cache-Control"] shouldBe "no-store"
                    }
                }
            }
        },
    )
