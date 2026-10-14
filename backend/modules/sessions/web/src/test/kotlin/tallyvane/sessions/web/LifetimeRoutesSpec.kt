package tallyvane.sessions.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.HttpRequestBuilder
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.APP_ORIGIN
import tallyvane.platform.http.Api
import tallyvane.platform.http.Caller
import tallyvane.platform.http.Callers
import tallyvane.platform.http.Surfaces
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.sessions.application.Harness
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000e1")

private const val ADMIN_HOST = "admin.example.test"

private var keys = 0

private fun nextKey(): String = "0199a000-0000-7000-8000-${(++keys).toString().padStart(12, '0')}"

private const val HOUR = 3_600_000L

private fun proposal(
    client: String = "browser",
    idle: Long = HOUR,
    absolute: Long = 24 * HOUR,
    freshness: Long = 300_000L,
    comment: String = "Tighter",
) = """{"client":"$client","idle_millis":$idle,"absolute_millis":$absolute,"freshness_millis":$freshness,""" +
    """"comment":"$comment"}"""

private fun HttpRequestBuilder.onTheAdminSite() {
    header(HttpHeaders.Host, ADMIN_HOST)
    header(HttpHeaders.Origin, "https://$ADMIN_HOST")
}

private suspend fun ApplicationTestBuilder.adding(body: String): HttpResponse =
    client.post("/api/v1/lifetime-versions") {
        onTheAdminSite()
        header("Idempotency-Key", nextKey())
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private suspend fun ApplicationTestBuilder.activating(client: String, number: Int, comment: String = "Go live") =
    this.client.post("/api/v1/lifetime-activations") {
        onTheAdminSite()
        header("Idempotency-Key", nextKey())
        contentType(ContentType.Application.Json)
        setBody("""{"client":"$client","number":$number,"comment":"$comment"}""")
    }

private suspend fun ApplicationTestBuilder.reading(path: String): HttpResponse =
    client.get("/api/v1/$path") { header(HttpHeaders.Host, ADMIN_HOST) }

/**
 * Runs [body] against the lifetime routes over the real use cases and fakes, as the signed-in holder of the
 * administrators' right, or as [holder] when told.
 */
private suspend fun asAdmin(
    fresh: Boolean = true,
    holder: Boolean = true,
    body: suspend ApplicationTestBuilder.(Harness) -> Unit,
) {
    val harness = Harness()
    if (holder) harness.admins.grant(Harness.ACCOUNT)
    val routes = LifetimeRoutesFactory()
    val account = Harness.ACCOUNT.value
    val api = Api(
        routes = listOf(
            routes.history(harness.listLifetimeVersions),
            routes.versions(harness.addLifetimeVersion),
            routes.previews(harness.previewLifetimeVersion),
            routes.activations(harness.activateLifetimeVersion),
        ),
        failures = FailureTranslator.Chained(emptyList()),
        trace = TraceHeader(IdGeneratorFake()),
        ledger = LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-14T09:00:00Z"))),
        callers = Callers { if (fresh) Caller.Confirmed(account, SESSION) else Caller.Signed(account, SESSION) },
        surfaces = Surfaces(APP_ORIGIN, "https://$ADMIN_HOST"),
    )
    testApplication {
        application { api.install(this) }
        body(harness)
    }
}

class LifetimeRoutesSpec :
    StringSpec(
        {
            "the history of a kind of client is read newest first, in the words of the API" {
                asAdmin {
                    adding(proposal(comment = "One hour")).status shouldBe HttpStatusCode.Created

                    val answer = reading("lifetime-history?client=browser")

                    answer.status shouldBe HttpStatusCode.OK
                    answer.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    val text = answer.bodyAsText()
                    text shouldContain """"client":"browser""""
                    text shouldContain """"made_by":"${Harness.ACCOUNT.value}""""
                    text shouldContain """"comment":"One hour""""
                    text shouldContain """"idle_millis":$HOUR"""
                    (text.indexOf(""""number":2""") < text.indexOf(""""number":1""")) shouldBe true
                    text shouldContain """"in_force":{"since":"""
                }
            }

            "the routes are not there through the console's door" {
                asAdmin {
                    client.get("/api/v1/lifetime-history?client=browser").status shouldBe HttpStatusCode.NotFound
                    client.get("/api/v1/lifetime-previews?client=browser&number=1").status shouldBe
                        HttpStatusCode.NotFound
                }
            }

            "a kind of client nobody has is a 422 naming the field" {
                asAdmin {
                    val answer = reading("lifetime-history?client=toaster")

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain """"field":"client","code":"unknown""""
                }
            }

            "someone without the right is a 403" {
                asAdmin(holder = false) {
                    reading("lifetime-history?client=browser").status shouldBe HttpStatusCode.Forbidden
                }
            }

            "lifetimes within the bounds are a 201 with the number of the version" {
                asAdmin {
                    val answer = adding(proposal())

                    answer.status shouldBe HttpStatusCode.Created
                    answer.bodyAsText() shouldBe """{"client":"browser","number":2}"""
                }
            }

            "lifetimes outside the bounds are a 422 with every error and words to show" {
                asAdmin {
                    val answer = adding(proposal(idle = 1, absolute = 1, comment = " "))

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    val text = answer.bodyAsText()
                    text shouldContain """{"field":"idle_millis","code":"out-of-bounds"}"""
                    text shouldContain """{"field":"comment","code":"comment-invalid"}"""
                    text shouldContain "milliseconds"
                }
            }

            "a body that is not a proposal is a 400" {
                asAdmin {
                    adding("""{"client":"browser"}""").status shouldBe HttpStatusCode.BadRequest
                }
            }

            "a preview says how many sessions are live and how many the version would end" {
                asAdmin { harness ->
                    harness.signedIn(Harness.OTHER_ACCOUNT)
                    harness.clock.advance(30.minutes)
                    adding(proposal(idle = 15 * 60_000L, absolute = HOUR)).status shouldBe HttpStatusCode.Created

                    val answer = reading("lifetime-previews?client=browser&number=2")

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"sessions":1,"would_end":1}"""
                }
            }

            "a preview of a version nobody kept is a 404, and so is a number that is not one" {
                asAdmin {
                    listOf("number=9", "number=abc", "").forEach { query ->
                        reading("lifetime-previews?client=browser&$query").status shouldBe HttpStatusCode.NotFound
                    }
                }
            }

            "activating needs a recent proof of who the person is" {
                asAdmin(fresh = false) {
                    val answer = activating("browser", 1)

                    answer.status shouldBe HttpStatusCode.Forbidden
                    answer.bodyAsText() shouldContain "step-up-required"
                }
            }

            "activating a kept version is a 204 and puts it in force" {
                asAdmin { harness ->
                    adding(proposal())

                    activating("browser", 2).status shouldBe HttpStatusCode.NoContent

                    reading("lifetime-history?client=browser").bodyAsText() shouldContain
                        """"in_force":{"since":"2026-10-02T09:00:00Z","by":"${Harness.ACCOUNT.value}","reason":"Go live"}"""
                    harness.endings().last() shouldBe TransactionRunnerFake.Ending.Committed
                }
            }

            "activating a version nobody kept is a 404" {
                asAdmin { activating("browser", 9).status shouldBe HttpStatusCode.NotFound }
            }
        },
    )
