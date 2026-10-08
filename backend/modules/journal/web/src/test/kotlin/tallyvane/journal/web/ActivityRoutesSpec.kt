package tallyvane.journal.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import tallyvane.identity.contract.AccountId
import tallyvane.journal.application.EntriesFake
import tallyvane.journal.application.Journal
import tallyvane.journal.application.SecurityNotifierFake
import tallyvane.journal.application.ShowActivityUseCase
import tallyvane.journal.contract.DeviceFacts
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val AT = Instant.parse("2026-10-06T09:00:00Z")
private val ADA = Uuid.parse("00000000-0000-7000-8000-000000000001")
private val STRANGER = Uuid.parse("00000000-0000-7000-8000-000000000002")
private val SESSION = Uuid.parse("00000000-0000-7000-8000-0000000000e1")
private val CHROME = DeviceFacts("chrome", "windows", false, "Work laptop")

private fun api(callers: Callers, entries: EntriesFake): Api = Api(
    routes = listOf(
        JournalRoutesFactory().activity(ShowActivityUseCase.ShowActivity(entries, TransactionRunnerFake())),
    ),
    failures = FailureTranslator.Chained(emptyList()),
    trace = TraceHeader(IdGeneratorFake()),
    ledger = LedgerFake(TransactionRunnerFake(), ClockFake(AT)),
    callers = callers,
    surfaces = Surfaces(APP_ORIGIN, "https://admin.example.test"),
)

private fun journalOver(entries: EntriesFake): Journal = Journal(entries, SecurityNotifierFake(), ClockFake(AT))

class ActivityRoutesSpec :
    StringSpec(
        {
            "shows a signed-in person their entries, the newest first, and is never cached" {
                testApplication {
                    val entries = EntriesFake()
                    journalOver(entries).apply {
                        signedIn(AccountId(ADA), SESSION, CHROME)
                        recoveryCodeSpent(AccountId(ADA), 7)
                    }
                    application { api(Callers { Caller.Signed(ADA, SESSION) }, entries).install(this) }

                    val answer = client.get("/api/v1/security-activity")

                    answer.status shouldBe HttpStatusCode.OK
                    answer.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    answer.bodyAsText() shouldBe
                        """{"entries":[""" +
                        """{"kind":"recovery_code_spent","occurred_at":"$AT",""" +
                        """"first_from_device":false,"codes_left":7},""" +
                        """{"kind":"signed_in","occurred_at":"$AT","device":{"browser":"chrome",""" +
                        """"platform":"windows","mobile":false,"name":"Work laptop"},""" +
                        """"first_from_device":true}]}"""
                }
            }

            "shows nobody else's entries" {
                testApplication {
                    val entries = EntriesFake()
                    journalOver(entries).signedIn(AccountId(STRANGER), SESSION, CHROME)
                    application { api(Callers { Caller.Signed(ADA, SESSION) }, entries).install(this) }

                    client.get("/api/v1/security-activity").bodyAsText() shouldBe """{"entries":[]}"""
                }
            }

            "gives a cursor while there is more, and the next page starts after it" {
                testApplication {
                    val entries = EntriesFake()
                    val journal = journalOver(entries)
                    repeat(31) { journal.recoveryCodeSpent(AccountId(ADA), it) }
                    application { api(Callers { Caller.Signed(ADA, SESSION) }, entries).install(this) }

                    val first = client.get("/api/v1/security-activity").bodyAsText()
                    first shouldContain """"next":"2""""
                    val second = client.get("/api/v1/security-activity?before=2").bodyAsText()

                    second shouldContain """"codes_left":0"""
                    second shouldNotContain """"codes_left":1"""
                    second shouldNotContain """"next""""
                }
            }

            "refuses a cursor no page gave" {
                testApplication {
                    application { api(Callers { Caller.Signed(ADA, SESSION) }, EntriesFake()).install(this) }

                    val answer = client.get("/api/v1/security-activity?before=not-a-number")

                    answer.status shouldBe HttpStatusCode.BadRequest
                    answer.bodyAsText() shouldContain "malformed"
                }
            }

            "is closed to somebody who is not signed in" {
                testApplication {
                    application { api(Callers.Anonymous(), EntriesFake()).install(this) }

                    val answer = client.get("/api/v1/security-activity")

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "sign-in-required"
                }
            }

            "tells a person whose session has lapsed to sign in again" {
                testApplication {
                    application { api(Callers { Caller.Lapsed() }, EntriesFake()).install(this) }

                    val answer = client.get("/api/v1/security-activity")

                    answer.status shouldBe HttpStatusCode.Unauthorized
                    answer.bodyAsText() shouldContain "session-expired"
                }
            }
        },
    )
