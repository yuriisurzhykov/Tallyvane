package tallyvane.platform.http.csrf

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication

class CompositeSpec :
    StringSpec({
        fun composite() = CsrfGuard.Composite(
            listOf(
                CsrfGuard.ContentTypeGuard(),
                CsrfGuard.OriginAllowlistGuard(setOf("https://app.tallyvane.com")),
            ),
        )

        "a request that satisfies every active guard is allowed" {
            testApplication {
                application { probeGuardedBy(composite()) }

                val answer = client.post("/probe") {
                    contentType(ContentType.Application.Json)
                    header("Origin", "https://app.tallyvane.com")
                }

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "failing only the content-type guard still refuses the whole composite" {
            testApplication {
                application { probeGuardedBy(composite()) }

                val answer = client.post("/probe") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    header("Origin", "https://app.tallyvane.com")
                }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "failing only the origin guard still refuses the whole composite" {
            testApplication {
                application { probeGuardedBy(composite()) }

                val answer = client.post("/probe") {
                    contentType(ContentType.Application.Json)
                    header("Origin", "https://attacker.example")
                }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }
    })
