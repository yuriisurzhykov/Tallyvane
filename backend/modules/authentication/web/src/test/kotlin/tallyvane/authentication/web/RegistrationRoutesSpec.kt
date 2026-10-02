package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

private suspend fun ApplicationTestBuilder.submit(cookie: String?, key: String, body: String) =
    client.post("/api/v1/registration") {
        header("Idempotency-Key", key)
        cookie?.let { header(HttpHeaders.Cookie, "__Host-attempt=$it") }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

class RegistrationRoutesSpec :
    StringSpec(
        {
            "creates the account of a person who chose a name and agreed, and answers 204" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val cookie = served.registering()

                    val answer =
                        submit(
                            cookie.revealed(),
                            "0199a000-0000-7000-8000-000000000001",
                            """{"name":"Ann","agreed":true}""",
                        )

                    answer.status shouldBe HttpStatusCode.NoContent
                    served.harness.accounts.knowing("sub-1") shouldBe true
                }
            }

            "refuses a form that was not agreed to, naming the field" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val cookie = served.registering()

                    val answer = submit(cookie.revealed(), "0199a000-0000-7000-8000-000000000002", """{"name":"Ann"}""")

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain """"field":"agreed""""
                    answer.bodyAsText() shouldContain """"code":"consent-required""""
                    served.harness.accounts.knowing("sub-1") shouldBe false
                }
            }

            "refuses a name identity does not accept, naming the field" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val cookie = served.registering()

                    val answer =
                        submit(
                            cookie.revealed(),
                            "0199a000-0000-7000-8000-000000000003",
                            """{"name":" ","agreed":true}""",
                        )

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain """"field":"name""""
                }
            }

            "answers 404 as a problem when the browser has no registration" {
                testApplication {
                    application { Served().api().install(this) }

                    val answer =
                        submit(null, "0199a000-0000-7000-8000-000000000004", """{"name":"Ann","agreed":true}""")

                    answer.status shouldBe HttpStatusCode.NotFound
                }
            }
        },
    )
