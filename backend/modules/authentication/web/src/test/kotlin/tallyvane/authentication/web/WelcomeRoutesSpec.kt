package tallyvane.authentication.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class WelcomeRoutesSpec :
    StringSpec(
        {
            "shows what Google said, and is never cached" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val cookie = served.registering()

                    val answer = client.get("/api/v1/welcome") {
                        header(HttpHeaders.Cookie, "__Host-attempt=${cookie.revealed()}")
                    }

                    answer.status shouldBe HttpStatusCode.OK
                    answer.bodyAsText() shouldBe """{"name":"Ann Example","email":"ann@example.com"}"""
                    answer.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                }
            }

            "answers 404 as a problem when there is no registration to finish" {
                testApplication {
                    application { Served().api().install(this) }

                    val answer = client.get("/api/v1/welcome")

                    answer.status shouldBe HttpStatusCode.NotFound
                    answer.headers[HttpHeaders.ContentType] shouldContain "application/problem+json"
                }
            }
        },
    )
