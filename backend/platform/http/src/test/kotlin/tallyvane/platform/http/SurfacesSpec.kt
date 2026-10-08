package tallyvane.platform.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import tallyvane.platform.kernel.Surface

private val SURFACES = Surfaces("https://app.example.test", "https://admin.example.test")

/**
 * The door a request to [host] came through, as a route behind [Surfaces] tells it.
 */
private suspend fun doorOf(host: String): String {
    var told = ""
    testApplication {
        application {
            routing { get("/door") { call.respondText(SURFACES.of(call).name) } }
        }
        told = client.get("/door") { header(HttpHeaders.Host, host) }.bodyAsText()
    }
    return told
}

class SurfacesSpec :
    StringSpec(
        {
            "a request to the administrators' host came through the administrators' door" {
                doorOf("admin.example.test") shouldBe Surface.Admin.name
            }

            "a request to the console's host came through the console's door" {
                doorOf("app.example.test") shouldBe Surface.App.name
            }

            "a host that is neither is the console's, the door with less power" {
                doorOf("localhost") shouldBe Surface.App.name
                doorOf("admin.example.test.evil.example") shouldBe Surface.App.name
            }

            "tells the origin of each door" {
                SURFACES.originOf(Surface.App) shouldBe "https://app.example.test"
                SURFACES.originOf(Surface.Admin) shouldBe "https://admin.example.test"
            }

            "refuses two doors on one host, because the host is all that tells them apart" {
                shouldThrow<IllegalArgumentException> {
                    Surfaces("https://app.example.test", "http://app.example.test:8081")
                }
            }
        },
    )
