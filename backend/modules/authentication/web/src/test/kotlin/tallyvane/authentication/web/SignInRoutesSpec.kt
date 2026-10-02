package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.fromApp

class SignInRoutesSpec :
    StringSpec(
        {
            "starting a sign-in answers where to send the browser and gives it the attempt's cookie" {
                testApplication {
                    application { Served().api().install(this) }

                    val answer = client.post("/api/v1/google-sign-in") {
                        fromApp()
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000001")
                    }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"authorization_url":"https://google.test/auth?state=secret-2"}"""
                    val cookies = answer.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                    cookies shouldHaveSize 1
                    cookies.single() shouldContain "__Host-attempt=secret-1"
                    cookies.single() shouldContain "HttpOnly"
                    cookies.single() shouldContain "Secure"
                    cookies.single() shouldContain "Path=/"
                    cookies.single() shouldContain "SameSite=Lax"
                    // `__Host-` is refused by the browser when a Domain is named.
                    (cookies.single().contains("Domain")) shouldBe false
                }
            }

            "a request without an Idempotency-Key is refused before it starts anything" {
                testApplication {
                    application { Served().api().install(this) }

                    client.post("/api/v1/google-sign-in") { fromApp() }.status shouldBe HttpStatusCode.BadRequest
                }
            }
        },
    )
