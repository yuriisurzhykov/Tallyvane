package tallyvane.authentication.web

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
import tallyvane.authentication.application.NumberOf
import tallyvane.authentication.application.PolicyDesk
import tallyvane.authentication.application.PolicyWords
import tallyvane.authentication.domain.Purpose
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000e1")

private const val ADMIN_HOST = "admin.example.test"

private var keys = 0

private fun nextKey(): String = "0199a000-0000-7000-8000-${(++keys).toString().padStart(12, '0')}"

private const val SECOND_FACTOR_IF_ENABLED =
    """{"accepts":["totp","recovery_code"],"necessity":"when_enrolled"}"""
private const val GOOGLE = """{"accepts":["google"],"necessity":"always"}"""
private const val SECOND_FACTOR_ALWAYS = """{"accepts":["totp","recovery_code"],"necessity":"always"}"""
private const val TOTP_ALWAYS = """{"accepts":["totp"],"necessity":"always"}"""

private fun proposal(purpose: String, vararg steps: String, maxFailures: Int = 5, comment: String = "Why not") =
    """{"purpose":"$purpose","steps":[${steps.joinToString(",")}],"attempt_lifetime_millis":300000,""" +
        """"max_failures":$maxFailures,"first_delay_millis":1000,"comment":"$comment"}"""

private fun HttpRequestBuilder.onTheAdminSite() {
    header(HttpHeaders.Host, ADMIN_HOST)
    header(HttpHeaders.Origin, ADMIN)
}

private suspend fun ApplicationTestBuilder.adding(body: String): HttpResponse = client.post("/api/v1/policy-versions") {
    onTheAdminSite()
    header("Idempotency-Key", nextKey())
    contentType(ContentType.Application.Json)
    setBody(body)
}

private suspend fun ApplicationTestBuilder.activating(purpose: String, number: Int, comment: String = "Go live") =
    client.post("/api/v1/policy-activations") {
        onTheAdminSite()
        header("Idempotency-Key", nextKey())
        contentType(ContentType.Application.Json)
        setBody("""{"purpose":"$purpose","number":$number,"comment":"$comment"}""")
    }

/**
 * Runs [body] against the policy routes over the real use cases and fakes, as the signed-in [who] of the desk.
 */
private suspend fun asAdmin(
    fresh: Boolean = true,
    who: (PolicyDesk) -> Uuid = { it.ann.value },
    body: suspend ApplicationTestBuilder.(PolicyDesk) -> Unit,
) {
    val desk = PolicyDesk()
    val routes = PolicyRoutesFactory(PolicyWords())
    val surfaces = Surfaces(APP, ADMIN)
    val account = who(desk)
    val api = Api(
        routes = listOf(
            routes.history(desk.list),
            routes.versions(desk.add),
            routes.previews(desk.preview),
            routes.activations(desk.activate),
        ),
        failures = FailureTranslator.Chained(emptyList()),
        trace = TraceHeader(IdGeneratorFake()),
        ledger = LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-14T09:00:00Z"))),
        callers = Callers { if (fresh) Caller.Confirmed(account, SESSION) else Caller.Signed(account, SESSION) },
        surfaces = surfaces,
    )
    testApplication {
        application { api.install(this) }
        body(desk)
    }
}

class PolicyRoutesSpec :
    StringSpec(
        {
            "the history of a purpose is read newest first, in the words of the API" {
                asAdmin { desk ->
                    adding(
                        proposal("login", GOOGLE, SECOND_FACTOR_IF_ENABLED, maxFailures = 3, comment = "Three tries"),
                    )
                        .status shouldBe HttpStatusCode.Created

                    val answer = client.get("/api/v1/policy-history?purpose=login") {
                        header(HttpHeaders.Host, ADMIN_HOST)
                    }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    val text = answer.bodyAsText()
                    text shouldContain """"purpose":"login""""
                    text shouldContain """"number":2,"made_by":"${desk.ann.value}""""
                    text shouldContain """"comment":"Three tries""""
                    text shouldContain """"steps":[{"accepts":["google"],"necessity":"always"},"""
                    text shouldContain """"max_failures":3"""
                    (text.indexOf(""""number":2""") < text.indexOf(""""number":1""")) shouldBe true
                    text shouldContain """"in_force":{"since":"2026-10-14T09:00:00Z""""
                }
            }

            "the routes are not there through the console's door" {
                asAdmin {
                    client.get("/api/v1/policy-history?purpose=login").status shouldBe HttpStatusCode.NotFound
                    client.get("/api/v1/policy-previews?purpose=login&number=1").status shouldBe HttpStatusCode.NotFound
                }
            }

            "a purpose nobody has is a 422 naming the field" {
                asAdmin {
                    val answer = client.get("/api/v1/policy-history?purpose=everything") {
                        header(HttpHeaders.Host, ADMIN_HOST)
                    }

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain """"field":"purpose","code":"unknown""""
                }
            }

            "someone without the right is a 403" {
                asAdmin(who = { it.cal.value }) {
                    client.get("/api/v1/policy-history?purpose=login") { header(HttpHeaders.Host, ADMIN_HOST) }
                        .status shouldBe HttpStatusCode.Forbidden
                }
            }

            "a version within the bounds is a 201 with its number" {
                asAdmin {
                    val answer = adding(proposal("login", GOOGLE, SECOND_FACTOR_IF_ENABLED))

                    answer.status shouldBe HttpStatusCode.Created
                    answer.bodyAsText() shouldBe """{"purpose":"login","number":2}"""
                }
            }

            "a policy that would lock people out is a 422 with every error and words to show" {
                asAdmin {
                    val answer =
                        adding(proposal("login", GOOGLE, SECOND_FACTOR_ALWAYS, maxFailures = 500, comment = " "))

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    val text = answer.bodyAsText()
                    text shouldContain """{"field":"max_failures","code":"out-of-bounds"}"""
                    text shouldContain """{"field":"steps","code":"would-lock-people-out"}"""
                    text shouldContain """{"field":"comment","code":"comment-invalid"}"""
                    text shouldContain "would demand a second factor from everyone"
                }
            }

            "a body that is not a proposal is a 400" {
                asAdmin {
                    adding("""{"purpose":"login"}""").status shouldBe HttpStatusCode.BadRequest
                }
            }

            "a preview says how many people a version reaches and how many could not pass" {
                asAdmin { desk ->
                    desk.withTotp(desk.ann)
                    adding(proposal("admin_login", GOOGLE, TOTP_ALWAYS)).status shouldBe HttpStatusCode.Created

                    val answer = client.get("/api/v1/policy-previews?purpose=admin_login&number=2") {
                        header(HttpHeaders.Host, ADMIN_HOST)
                    }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"people":2,"cannot_pass":1}"""
                }
            }

            "a preview of a version nobody kept is a 404, and so is a number that is not one" {
                asAdmin {
                    listOf("number=9", "number=abc", "").forEach { query ->
                        client.get("/api/v1/policy-previews?purpose=login&$query") {
                            header(HttpHeaders.Host, ADMIN_HOST)
                        }.status shouldBe HttpStatusCode.NotFound
                    }
                }
            }

            "activating needs a recent proof of who the person is" {
                asAdmin(fresh = false) {
                    val answer = activating("login", 1)

                    answer.status shouldBe HttpStatusCode.Forbidden
                    answer.bodyAsText() shouldContain "step-up-required"
                }
            }

            "activating a kept version is a 204 and puts it in force" {
                asAdmin { desk ->
                    adding(proposal("login", GOOGLE, SECOND_FACTOR_IF_ENABLED, maxFailures = 3))

                    activating("login", 2).status shouldBe HttpStatusCode.NoContent

                    desk.versions.active(Purpose.Login)
                        ?.let { NumberOf(it).number() } shouldBe 2
                }
            }

            "activating a version no administrator could pass is a 422 with its own code" {
                asAdmin { desk ->
                    desk.withRecoveryCodesOnly(desk.ann)
                    desk.withRecoveryCodesOnly(desk.bob)
                    adding(proposal("admin_login", GOOGLE, TOTP_ALWAYS)).status shouldBe HttpStatusCode.Created

                    val answer = activating("admin_login", 2)

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain
                        """{"field":"number","code":"would-lock-out-every-administrator"}"""
                }
            }

            "activating a version nobody kept is a 404" {
                asAdmin { activating("login", 9).status shouldBe HttpStatusCode.NotFound }
            }
        },
    )
