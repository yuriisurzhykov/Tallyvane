package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import tallyvane.authentication.application.Harness
import tallyvane.platform.http.fromApp
import tallyvane.platform.kernel.Secret

private var keys = 0

private suspend fun ApplicationTestBuilder.answer(cookie: Secret?, body: String): HttpResponse =
    client.post("/api/v1/second-factor-codes") {
        fromApp()
        header("Idempotency-Key", "0199a000-0000-7000-8000-${(++keys).toString().padStart(12, '0')}")
        cookie?.let { header(HttpHeaders.Cookie, "__Host-attempt=${it.revealed()}") }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private suspend fun ApplicationTestBuilder.standing(cookie: Secret?): HttpResponse = client.get("/api/v1/sign-in") {
    cookie?.let { header(HttpHeaders.Cookie, "__Host-attempt=${it.revealed()}") }
}

private suspend fun withTotp(body: suspend ApplicationTestBuilder.(Harness, Secret, String) -> Unit) {
    val served = Served()
    served.harness.accounts.knows("sub-1")
    val setup = served.harness.enableTotp("sub-1")
    val attempt = served.harness.signedInWithGoogle("sub-1")
    testApplication {
        application { served.api().install(this) }
        body(served.harness, attempt, setup.app.codeAt(served.harness.clock.now()))
    }
}

class SecondFactorCodeRoutesSpec :
    StringSpec(
        {
            "a sign-in with TOTP on says it waits for a code, and for which kinds" {
                withTotp { _, attempt, _ ->
                    val shown = standing(attempt)

                    shown.status shouldBe HttpStatusCode.OK
                    shown.bodyAsText() shouldBe """{"state":"awaiting","factors":["totp","recovery_code"]}"""
                    shown.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                }
            }

            "a right code is answered 204 and the sign-in then says it is complete" {
                withTotp { _, attempt, code ->
                    val answered = answer(attempt, """{"kind":"totp","code":"$code"}""")

                    answered.status shouldBe HttpStatusCode.NoContent
                    standing(attempt).bodyAsText() shouldBe """{"state":"complete","factors":[]}"""
                }
            }

            "a recovery code is answered 200 with how many are left" {
                val served = Served()
                served.harness.accounts.knows("sub-1")
                val setup = served.harness.enableTotp("sub-1")
                val attempt = served.harness.signedInWithGoogle("sub-1")
                testApplication {
                    application { served.api().install(this) }

                    val answered =
                        answer(attempt, """{"kind":"recovery_code","code":"${setup.recoveryCodes.first()}"}""")

                    answered.status shouldBe HttpStatusCode.OK
                    answered.bodyAsText() shouldBe """{"recovery_codes_remaining":9}"""
                }
            }

            "a wrong code is a 422 that names the field and says how long to wait" {
                withTotp { _, attempt, _ ->
                    val answered = answer(attempt, """{"kind":"totp","code":"000000"}""")

                    answered.status shouldBe HttpStatusCode.UnprocessableEntity
                    answered.bodyAsText() shouldContain """"field":"code""""
                    answered.bodyAsText() shouldContain """"code":"wrong-code""""
                    answered.headers[HttpHeaders.RetryAfter] shouldBe "1"
                }
            }

            "a code typed during a pause is a 429 with the time left, and the page is told the same" {
                withTotp { _, attempt, code ->
                    answer(attempt, """{"kind":"totp","code":"000000"}""")

                    val answered = answer(attempt, """{"kind":"totp","code":"$code"}""")

                    answered.status shouldBe HttpStatusCode.TooManyRequests
                    answered.headers[HttpHeaders.ContentType] shouldContain "application/problem+json"
                    answered.bodyAsText() shouldContain "slow-down"
                    answered.headers[HttpHeaders.RetryAfter] shouldBe "1"
                    standing(attempt).bodyAsText() shouldBe
                        """{"state":"paused","factors":["totp","recovery_code"],"retry_after":1}"""
                }
            }

            "a browser with no sign-in is told it is gone, and has nothing to look at" {
                withTotp { _, _, code ->
                    val answered = answer(null, """{"kind":"totp","code":"$code"}""")

                    answered.status shouldBe HttpStatusCode.Gone
                    answered.bodyAsText() shouldContain "gone"
                    standing(null).status shouldBe HttpStatusCode.NotFound
                }
            }

            "a sign-in with nothing more to ask is a 409" {
                val served = Served()
                served.harness.accounts.knows("sub-1")
                val attempt = served.harness.signedInWithGoogle("sub-1")
                testApplication {
                    application { served.api().install(this) }

                    val answered = answer(attempt, """{"kind":"totp","code":"123456"}""")

                    answered.status shouldBe HttpStatusCode.Conflict
                }
            }

            "a kind nobody knows is a body that cannot be read" {
                withTotp { _, attempt, _ ->
                    val answered = answer(attempt, """{"kind":"sms","code":"123456"}""")

                    answered.status shouldBe HttpStatusCode.BadRequest
                }
            }
        },
    )
