package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
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
import tallyvane.authentication.application.AuthenticatorApp
import tallyvane.platform.http.Caller
import tallyvane.platform.http.Callers
import tallyvane.platform.http.fromApp

private var keys = 0

private fun nextKey(): String = "0199a000-0000-7000-8000-${(++keys).toString().padStart(12, '0')}"

private suspend fun ApplicationTestBuilder.begin(): HttpResponse = client.post("/api/v1/totp-enrollments") {
    fromApp()
    header("Idempotency-Key", nextKey())
}

private suspend fun ApplicationTestBuilder.confirm(code: String): HttpResponse =
    client.post("/api/v1/totp-confirmations") {
        fromApp()
        header("Idempotency-Key", nextKey())
        contentType(ContentType.Application.Json)
        setBody("""{"code":"$code"}""")
    }

private suspend fun ApplicationTestBuilder.disable(): HttpResponse = client.delete("/api/v1/totp-enrollment") {
    fromApp()
    header("Idempotency-Key", nextKey())
}

private suspend fun ApplicationTestBuilder.reissue(): HttpResponse = client.post("/api/v1/recovery-codes") {
    fromApp()
    header("Idempotency-Key", nextKey())
}

private suspend fun ApplicationTestBuilder.standing(): String = client.get("/api/v1/second-factor").bodyAsText()

private suspend fun signedInAs(fresh: Boolean, body: suspend ApplicationTestBuilder.(Served) -> Unit) {
    val served = Served()
    served.harness.accounts.knows("sub-1")
    val account = checkNotNull(served.harness.accounts.withGoogle("sub-1")).value
    val callers = Callers { if (fresh) Caller.Confirmed(account) else Caller.Signed(account) }
    testApplication {
        application { served.api(callers).install(this) }
        body(served)
    }
}

class TotpRoutesSpec :
    StringSpec(
        {
            "turning TOTP on: begin tells the key once, the first code tells ten recovery codes once" {
                signedInAs(fresh = true) { served ->
                    standing() shouldBe """{"standing":"off","recovery_codes_remaining":0}"""

                    val begun = begin()
                    begun.status shouldBe HttpStatusCode.OK
                    begun.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    val key = Regex(""""key":"([A-Z2-7]+)"""").find(begun.bodyAsText())!!.groupValues[1]
                    begun.bodyAsText() shouldContain """"uri":"otpauth://totp/Tallyvane?secret=$key"""

                    val confirmed = confirm(AuthenticatorApp(key).codeAt(served.harness.clock.now()))
                    confirmed.status shouldBe HttpStatusCode.OK
                    confirmed.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    Regex("CODE\\d+").findAll(confirmed.bodyAsText()).count() shouldBe 10
                    standing() shouldBe """{"standing":"active","recovery_codes_remaining":10}"""
                }
            }

            "a wrong first code is a 422 and leaves the set-up waiting" {
                signedInAs(fresh = true) { _ ->
                    begin()

                    val confirmed = confirm("000000")

                    confirmed.status shouldBe HttpStatusCode.UnprocessableEntity
                    confirmed.bodyAsText() shouldContain """"code":"wrong-code""""
                    standing() shouldBe """{"standing":"off","recovery_codes_remaining":0}"""
                }
            }

            "a first code with nothing begun is a 409" {
                signedInAs(fresh = true) { _ ->
                    confirm("123456").status shouldBe HttpStatusCode.Conflict
                }
            }

            "beginning, turning off and new recovery codes ask for a recent proof, and a first code does not" {
                signedInAs(fresh = false) { _ ->
                    for (answer in listOf(begin(), disable(), reissue())) {
                        answer.status shouldBe HttpStatusCode.Forbidden
                        answer.bodyAsText() shouldContain "step-up-required"
                    }
                    confirm("123456").status shouldBe HttpStatusCode.Conflict
                    standing() shouldBe """{"standing":"off","recovery_codes_remaining":0}"""
                }
            }

            "everything here is closed to somebody who is not signed in" {
                val served = Served()
                testApplication {
                    application { served.api().install(this) }

                    for (answer in listOf(begin(), confirm("123456"), disable(), reissue())) {
                        answer.status shouldBe HttpStatusCode.Unauthorized
                    }
                    client.get("/api/v1/second-factor").status shouldBe HttpStatusCode.Unauthorized
                }
            }

            "beginning while TOTP is on is a 409" {
                signedInAs(fresh = true) { served ->
                    served.harness.enableTotp("sub-1")

                    begin().status shouldBe HttpStatusCode.Conflict
                }
            }

            "new recovery codes replace the set, and need TOTP to be on" {
                signedInAs(fresh = true) { served ->
                    reissue().status shouldBe HttpStatusCode.Conflict
                    served.harness.enableTotp("sub-1")

                    val reissued = reissue()

                    reissued.status shouldBe HttpStatusCode.OK
                    reissued.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    reissued.bodyAsText() shouldContain """"recovery_codes":["""
                }
            }

            "turning TOTP off is a 204, and again is a 409" {
                signedInAs(fresh = true) { served ->
                    served.harness.enableTotp("sub-1")

                    disable().status shouldBe HttpStatusCode.NoContent
                    standing() shouldBe """{"standing":"off","recovery_codes_remaining":0}"""
                    disable().status shouldBe HttpStatusCode.Conflict
                }
            }
        },
    )
