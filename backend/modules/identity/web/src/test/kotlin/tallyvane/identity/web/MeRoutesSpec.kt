package tallyvane.identity.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import tallyvane.identity.application.AccountDirectory
import tallyvane.identity.application.KeptAccountsFake
import tallyvane.identity.application.WhoAmIUseCase
import tallyvane.identity.contract.Registrant
import tallyvane.platform.http.APP_ORIGIN
import tallyvane.platform.http.Api
import tallyvane.platform.http.Caller
import tallyvane.platform.http.Callers
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val AT = Instant.parse("2026-10-02T09:00:00Z")
private val FIRST = Uuid.parse("00000000-0000-7000-8000-000000000001")
private val SESSION = Uuid.parse("00000000-0000-7000-8000-0000000000e1")

private fun api(callers: Callers, kept: KeptAccountsFake): Api = Api(
    routes = listOf(IdentityRoutesFactory().me(WhoAmIUseCase.WhoAmI(kept, TransactionRunnerFake()))),
    failures = FailureTranslator.Chained(emptyList()),
    trace = TraceHeader(IdGeneratorFake()),
    ledger = LedgerFake(TransactionRunnerFake(), ClockFake(AT)),
    callers = callers,
    appOrigin = APP_ORIGIN,
)

private suspend fun keptWithAda(): KeptAccountsFake = KeptAccountsFake().also {
    AccountDirectory(it, IdGeneratorFake()).register(Registrant("google-1", "Ada Lovelace", "ada@example.com", AT))
}

class MeRoutesSpec :
    StringSpec(
        {
            "tells a signed-in person who they are, and is never cached" {
                testApplication {
                    val kept = keptWithAda()
                    application { api(Callers { Caller.Signed(FIRST, SESSION) }, kept).install(this) }

                    val answer = client.get("/api/v1/me")

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"id":"$FIRST","name":"Ada Lovelace"}"""
                    answer.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                }
            }

            "is closed to somebody who is not signed in" {
                testApplication {
                    application { api(Callers.Anonymous(), KeptAccountsFake()).install(this) }

                    val answer = client.get("/api/v1/me")

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "sign-in-required"
                }
            }

            "tells a person whose session has lapsed to sign in again" {
                testApplication {
                    application { api(Callers { Caller.Lapsed() }, KeptAccountsFake()).install(this) }

                    val answer = client.get("/api/v1/me")

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "session-expired"
                }
            }

            "tells a person whose account is gone that their session ended" {
                testApplication {
                    application { api(Callers { Caller.Signed(FIRST, SESSION) }, KeptAccountsFake()).install(this) }

                    val answer = client.get("/api/v1/me")

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "session-expired"
                }
            }
        },
    )
