package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleProfile
import tallyvane.platform.http.fromApp

private fun ApplicationTestBuilder.browser(): HttpClient = createClient { followRedirects = false }

class StepUpRoutesSpec :
    StringSpec(
        {
            "starting a confirmation answers where to send the browser and gives it the attempt's cookie" {
                testApplication {
                    application { Served().api().install(this) }

                    val answer = client.post("/api/v1/google-step-up") {
                        fromApp()
                        header("Idempotency-Key", "0199a000-0000-7000-8000-000000000001")
                    }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"authorization_url":"https://google.test/auth?state=secret-2"}"""
                    answer.headers.getAll(HttpHeaders.SetCookie).orEmpty().single() shouldContain
                        "__Host-attempt=secret-1"
                }
            }

            "a confirmation that comes back from Google lands on the page that tells the window to go on" {
                testApplication {
                    val served = Served()
                    served.harness.accounts.knows("sub-1")
                    application { served.api().install(this) }
                    val pressed = served.harness.pressStepUp()
                    val code = served.harness.google.arrange(
                        pressed.state,
                        GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com")),
                    )

                    val answer = browser().get("/api/v1/google-return?code=$code&state=${pressed.state}") {
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.status shouldBe HttpStatusCode.Found
                    answer.headers[HttpHeaders.Location] shouldBe "$APP/step-up/continue"
                }
            }
        },
    )
