package tallyvane.platform.http.csrf

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class DoubleSubmitGuardSpec :
    StringSpec({
        "a GET is exempt even with no cookie or header at all" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.DoubleSubmitGuard()) }

                val answer = client.get("/probe")

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "a matching cookie and header allow a state-changing request" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.DoubleSubmitGuard()) }

                val answer = client.post("/probe") {
                    cookie(CsrfGuard.DoubleSubmitGuard.DEFAULT_COOKIE_NAME, "same-value")
                    header(CsrfGuard.DoubleSubmitGuard.DEFAULT_HEADER_NAME, "same-value")
                }

                answer.status shouldBe HttpStatusCode.OK
            }
        }

        "a mismatched cookie and header refuses — the attack this guard exists for" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.DoubleSubmitGuard()) }

                val answer = client.post("/probe") {
                    cookie(CsrfGuard.DoubleSubmitGuard.DEFAULT_COOKIE_NAME, "victims-value")
                    header(CsrfGuard.DoubleSubmitGuard.DEFAULT_HEADER_NAME, "attackers-guess")
                }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a cookie with no matching header is refused — the cross-site form-post shape" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.DoubleSubmitGuard()) }

                val answer = client.post("/probe") {
                    cookie(CsrfGuard.DoubleSubmitGuard.DEFAULT_COOKIE_NAME, "victims-value")
                }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a header with no cookie at all is refused" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.DoubleSubmitGuard()) }

                val answer = client.post("/probe") {
                    header(CsrfGuard.DoubleSubmitGuard.DEFAULT_HEADER_NAME, "whatever")
                }

                answer.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a configured cookie and header name are honoured instead of the defaults" {
            testApplication {
                application { probeGuardedBy(CsrfGuard.DoubleSubmitGuard("my_cookie", "X-My-Header")) }

                val answer = client.post("/probe") {
                    cookie("my_cookie", "same-value")
                    header("X-My-Header", "same-value")
                }

                answer.status shouldBe HttpStatusCode.OK
            }
        }
    })
