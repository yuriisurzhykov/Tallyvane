package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleProfile
import tallyvane.platform.kernel.Surface

private fun ApplicationTestBuilder.browser(): HttpClient = createClient { followRedirects = false }

private fun vouched() = GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com"))

class GoogleReturnRoutesSpec :
    StringSpec(
        {
            "a person with no account is sent to the welcome form with a cookie of their registration" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val pressed = served.harness.pressSignIn()
                    val code = served.harness.google.arrange(pressed.state, vouched())

                    val answer = browser().get("/api/v1/google-return?code=$code&state=${pressed.state}") {
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.status shouldBe HttpStatusCode.Found
                    answer.headers[HttpHeaders.Location] shouldBe "$APP/welcome"
                    answer.headers.getAll(HttpHeaders.SetCookie).orEmpty().single() shouldContain
                        "__Host-attempt=secret-5"
                }
            }

            "a person with an account goes on, with the cookie they already hold" {
                testApplication {
                    val served = Served()
                    served.harness.accounts.knows("sub-1")
                    application { served.api().install(this) }
                    val pressed = served.harness.pressSignIn()
                    val code = served.harness.google.arrange(pressed.state, vouched())

                    val answer = browser().get("/api/v1/google-return?code=$code&state=${pressed.state}") {
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.headers[HttpHeaders.Location] shouldBe "$APP/login/continue"
                    answer.headers.getAll(HttpHeaders.SetCookie) shouldBe null
                }
            }

            "a browser with no cookie is sent back to sign in, and told to forget what it has" {
                testApplication {
                    application { Served().api().install(this) }

                    val answer = browser().get("/api/v1/google-return?code=any&state=any")

                    answer.headers[HttpHeaders.Location] shouldBe "$APP/login?problem=restart"
                    answer.headers.getAll(HttpHeaders.SetCookie).orEmpty().single() shouldContain "Max-Age=0"
                }
            }

            "a person who said no at Google is sent back with the reason" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val pressed = served.harness.pressSignIn()

                    val answer = browser().get("/api/v1/google-return?error=access_denied") {
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.headers[HttpHeaders.Location] shouldBe "$APP/login?problem=cancelled"
                }
            }

            "nothing in the request chooses where the browser lands" {
                testApplication {
                    application { Served().api().install(this) }

                    val evil = "next=https://evil.example&redirect_uri=https://evil.example"
                    val answer = browser().get("/api/v1/google-return?code=any&state=any&$evil")

                    answer.headers[HttpHeaders.Location]!!.startsWith("$APP/") shouldBe true
                }
            }

            "a person who comes back on the administrators' host lands on the administrators' pages" {
                testApplication {
                    val served = Served()
                    served.harness.accounts.knows("sub-1")
                    application { served.api().install(this) }
                    val pressed = served.harness.pressSignIn(Surface.Admin)
                    val code = served.harness.google.arrange(pressed.state, vouched())

                    val answer = browser().get("/api/v1/google-return?code=$code&state=${pressed.state}") {
                        header(HttpHeaders.Host, "admin.example.test")
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.headers[HttpHeaders.Location] shouldBe "$ADMIN/login/continue"
                }
            }

            "a stranger to the administrators' site is sent back to its sign-in page, refused, and not registered" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val pressed = served.harness.pressSignIn(Surface.Admin)
                    val code = served.harness.google.arrange(pressed.state, vouched())

                    val answer = browser().get("/api/v1/google-return?code=$code&state=${pressed.state}") {
                        header(HttpHeaders.Host, "admin.example.test")
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.headers[HttpHeaders.Location] shouldBe "$ADMIN/login?problem=refused"
                    served.harness.accounts.knowing("sub-1") shouldBe false
                }
            }

            "a return through the other host than the one the sign-in left from is turned back" {
                testApplication {
                    val served = Served()
                    served.harness.accounts.knows("sub-1")
                    application { served.api().install(this) }
                    val pressed = served.harness.pressSignIn(Surface.App)
                    val code = served.harness.google.arrange(pressed.state, vouched())

                    val answer = browser().get("/api/v1/google-return?code=$code&state=${pressed.state}") {
                        header(HttpHeaders.Host, "admin.example.test")
                        header(HttpHeaders.Cookie, "__Host-attempt=${pressed.attempt.revealed()}")
                    }

                    answer.headers[HttpHeaders.Location] shouldBe "$ADMIN/login?problem=refused"
                }
            }
        },
    )
