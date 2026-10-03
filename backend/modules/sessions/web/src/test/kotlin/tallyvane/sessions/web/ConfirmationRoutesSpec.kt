package tallyvane.sessions.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.fromApp
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.application.Harness
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Duration.Companion.minutes

private const val MAC_CHROME =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private var keys = 0

private fun nextKey(): String = "0199a000-0000-7000-8000-%012d".format(500 + keys++)

private suspend fun ApplicationTestBuilder.signOutOthers(session: Secret?): HttpResponse =
    client.delete("/api/v1/other-devices") {
        fromApp()
        header("Idempotency-Key", nextKey())
        session?.let { withCookie("__Host-session", it) }
    }

private suspend fun ApplicationTestBuilder.signOutHere(session: Secret): HttpResponse =
    client.delete("/api/v1/session") {
        fromApp()
        header("Idempotency-Key", nextKey())
        withCookie("__Host-session", session)
    }

private suspend fun ApplicationTestBuilder.confirm(session: Secret?, attempt: Secret?): HttpResponse =
    client.post("/api/v1/step-ups") {
        fromApp()
        header("Idempotency-Key", nextKey())
        session?.let { withCookie("__Host-session", it) }
        attempt?.let { withCookie("__Host-attempt", it) }
    }

class ConfirmationRoutesSpec :
    StringSpec(
        {
            "a person who has just signed in may end the sessions on other devices" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    served.harness.signedIn(agent = UserAgent(MAC_CHROME))

                    signOutOthers(here).status shouldBe HttpStatusCode.NoContent
                }
            }

            "a person whose proof has gone stale is told to confirm, and nothing is ended" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    val there = served.harness.signedIn(agent = UserAgent(MAC_CHROME))
                    served.harness.clock.advance(6.minutes)

                    val answer = signOutOthers(here)

                    answer.status shouldBe HttpStatusCode.Forbidden
                    answer.bodyAsText() shouldContain "step-up-required"
                    client.get("/api/v1/devices") { withCookie("__Host-session", there) }.status shouldBe
                        HttpStatusCode.OK
                }
            }

            "a stale session may still sign out on the device it is on, which asks for no proof" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    served.harness.clock.advance(6.minutes)

                    signOutHere(here).status shouldBe HttpStatusCode.NoContent
                }
            }

            "confirming makes the dangerous act possible again, with the same cookie" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    served.harness.clock.advance(6.minutes)
                    signOutOthers(here).status shouldBe HttpStatusCode.Forbidden

                    val confirmed = confirm(here, served.harness.finishedConfirming())

                    confirmed.status shouldBe HttpStatusCode.NoContent
                    confirmed.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                        .none { it.startsWith("__Host-session=") } shouldBe true
                    signOutOthers(here).status shouldBe HttpStatusCode.NoContent
                }
            }

            "another person's confirmation is refused as not theirs, and the act stays closed" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    served.harness.clock.advance(6.minutes)

                    val answer = confirm(here, served.harness.finishedConfirming(account = Harness.OTHER_ACCOUNT))

                    answer.status shouldBe HttpStatusCode.Forbidden
                    signOutOthers(here).bodyAsText() shouldContain "step-up-required"
                }
            }

            "a confirmation that is not there, or is spent, is a conflict" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    val attempt = served.harness.finishedConfirming()
                    confirm(here, attempt)

                    confirm(here, attempt).status shouldBe HttpStatusCode.Conflict
                    confirm(here, null).status shouldBe HttpStatusCode.Conflict
                }
            }

            "is closed to a request with no session, and to one whose session nobody issued" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val attempt = served.harness.finishedConfirming()

                    confirm(null, attempt).status shouldBe HttpStatusCode.Unauthorized
                    confirm(Secret("never-issued"), attempt).status shouldBe HttpStatusCode.Unauthorized
                }
            }
        },
    )
