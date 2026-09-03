package tallyvane.platform.http.csrf

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication

class ContentTypeGuardSpec :
    StringSpec({
        "a GET is exempt regardless of its content type" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.ContentTypeGuard()) }

                val answer = client.get("/probe")

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "a POST with application/json is allowed" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.ContentTypeGuard()) }

                val answer = client.post("/probe") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "a POST shaped like an HTML form submission is refused — the vector this guard exists for" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.ContentTypeGuard()) }

                val answer = client.post("/probe") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("a=1")
                }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a POST with no content type at all is refused, not assumed to be JSON" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.ContentTypeGuard()) }

                val answer = client.post("/probe")

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a charset parameter on an otherwise-JSON content type does not defeat the check" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.ContentTypeGuard()) }

                val answer = client.post("/probe") {
                    contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
                    setBody("{}")
                }

                answer.status shouldBe HttpStatusCode.OK
            }
        }
    })
