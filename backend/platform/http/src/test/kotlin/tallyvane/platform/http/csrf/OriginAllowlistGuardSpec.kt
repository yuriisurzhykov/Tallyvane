package tallyvane.platform.http.csrf

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class OriginAllowlistGuardSpec :
    StringSpec({
        "a GET is exempt regardless of its Origin" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.OriginAllowlistGuard(setOf("https://app.tallyvane.com"))) }

                val answer = client.get("/probe")

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "an allowed Origin is let through" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.OriginAllowlistGuard(setOf("https://app.tallyvane.com"))) }

                val answer = client.post("/probe") { header("Origin", "https://app.tallyvane.com") }

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "an Origin outside the allow-list is refused — a cross-site request naming its own origin" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.OriginAllowlistGuard(setOf("https://app.tallyvane.com"))) }

                val answer = client.post("/probe") { header("Origin", "https://attacker.example") }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a missing Origin on a state-changing request is refused, not assumed same-origin" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.OriginAllowlistGuard(setOf("https://app.tallyvane.com"))) }

                val answer = client.post("/probe")

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }
    })
