package tallyvane.identity.web.oauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class GoogleOAuthCallbackSpec :
    StringSpec({
        "the callback contract can be replaced at an HTTP boundary" {
            val callback = RecordingCallback()
            testApplication {
                application {
                    routing {
                        get("/callback") {
                            callback.handle(call)
                        }
                    }
                }
                client.get("/callback").status shouldBe HttpStatusCode.NoContent
            }
            callback.invocations shouldBe 1
        }
    })

private class RecordingCallback : GoogleOAuthCallback {
    var invocations: Int = 0
        private set

    override suspend fun handle(call: io.ktor.server.application.ApplicationCall) {
        invocations += 1
        call.respond(HttpStatusCode.NoContent)
    }
}
