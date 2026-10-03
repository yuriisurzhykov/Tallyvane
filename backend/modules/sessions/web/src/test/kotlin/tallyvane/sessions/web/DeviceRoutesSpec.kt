package tallyvane.sessions.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import tallyvane.platform.http.fromApp
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.application.Harness
import tallyvane.sessions.domain.UserAgent
import kotlin.uuid.Uuid

private const val MAC_CHROME =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private var keys = 0

private fun nextKey(): String = "0199a000-0000-7000-8000-%012d".format(100 + keys++)

private suspend fun ApplicationTestBuilder.devices(session: Secret?): HttpResponse =
    client.get("/api/v1/devices") { session?.let { withCookie("__Host-session", it) } }

private suspend fun ApplicationTestBuilder.signOutOn(id: String, session: Secret?): HttpResponse =
    client.delete("/api/v1/device/$id") {
        fromApp()
        header("Idempotency-Key", nextKey())
        session?.let { withCookie("__Host-session", it) }
    }

private suspend fun ApplicationTestBuilder.name(id: String, session: Secret?, body: String): HttpResponse =
    client.put("/api/v1/device-names/$id") {
        fromApp()
        header("Idempotency-Key", nextKey())
        contentType(ContentType.Application.Json)
        setBody(body)
        session?.let { withCookie("__Host-session", it) }
    }

private suspend fun ApplicationTestBuilder.signOutOthers(session: Secret?): HttpResponse =
    client.delete("/api/v1/other-devices") {
        fromApp()
        header("Idempotency-Key", nextKey())
        session?.let { withCookie("__Host-session", it) }
    }

private suspend fun ApplicationTestBuilder.idsOf(session: Secret, current: Boolean): List<String> =
    Regex("\"id\":\"([^\"]+)\"[^}]*\"current\":$current")
        .findAll(devices(session).bodyAsText())
        .map { it.groupValues[1] }
        .toList()

class DeviceRoutesSpec :
    StringSpec(
        {
            "lists the devices as parts, with the one asking marked, and is never cached" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val secret = served.harness.signedIn()

                    val answer = devices(secret)

                    answer.status shouldBe HttpStatusCode.OK
                    answer.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                    val body = answer.bodyAsText()
                    body shouldContain "\"browser\":\"chrome\""
                    body shouldContain "\"platform\":\"windows\""
                    body shouldContain "\"mobile\":false"
                    body shouldContain "\"current\":true"
                }
            }

            "keeps the User-Agent the browser sent when it exchanged its sign-in, as the device" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val attempt = served.harness.finishedSigningIn()
                    val exchanged = client.post("/api/v1/sessions") {
                        fromApp()
                        header("Idempotency-Key", nextKey())
                        header(HttpHeaders.UserAgent, MAC_CHROME)
                        withCookie("__Host-attempt", attempt)
                    }
                    val secret = Secret(
                        exchanged.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                            .single { it.startsWith("__Host-session=") }.substringAfter("=").substringBefore(";"),
                    )

                    val body = devices(secret).bodyAsText()

                    body shouldContain "\"platform\":\"macos\""
                }
            }

            "is closed to a request with no session, and to one whose session nobody issued" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val someId = Uuid.parse("0199a000-0000-7000-8000-0000000000ff").toString()

                    devices(null).status shouldBe HttpStatusCode.Unauthorized
                    devices(Secret("never-issued")).status shouldBe HttpStatusCode.Unauthorized
                    signOutOn(someId, null).status shouldBe HttpStatusCode.Unauthorized
                    name(someId, null, """{"name":"x"}""").status shouldBe HttpStatusCode.Unauthorized
                    signOutOthers(null).status shouldBe HttpStatusCode.Unauthorized
                    devices(null).headers[HttpHeaders.ContentType] shouldStartWith "application/problem+json"
                }
            }

            "signs out on another device, which then gets 401, while this one stays signed in" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    val there = served.harness.signedIn(agent = UserAgent(MAC_CHROME))
                    val otherId = idsOf(here, current = false).single()

                    signOutOn(otherId, here).status shouldBe HttpStatusCode.NoContent

                    devices(there).status shouldBe HttpStatusCode.Unauthorized
                    devices(here).status shouldBe HttpStatusCode.OK
                }
            }

            "answers 404 for an id that is not a device of the person, and for one that is not an id" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val mine = served.harness.signedIn()
                    val strangers = served.harness.signedIn(account = Harness.OTHER_ACCOUNT)
                    val strangersId = idsOf(strangers, current = true).single()

                    signOutOn(strangersId, mine).status shouldBe HttpStatusCode.NotFound
                    signOutOn("not-an-id", mine).status shouldBe HttpStatusCode.NotFound
                    name(strangersId, mine, """{"name":"mine now"}""").status shouldBe HttpStatusCode.NotFound
                    devices(strangers).status shouldBe HttpStatusCode.OK
                }
            }

            "signs out everywhere but here" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    val second = served.harness.signedIn()
                    val third = served.harness.signedIn(agent = UserAgent(MAC_CHROME))

                    signOutOthers(here).status shouldBe HttpStatusCode.NoContent

                    devices(second).status shouldBe HttpStatusCode.Unauthorized
                    devices(third).status shouldBe HttpStatusCode.Unauthorized
                    devices(here).status shouldBe HttpStatusCode.OK
                }
            }

            "names a device, and the name is listed" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    val id = idsOf(here, current = true).single()

                    name(id, here, """{"name":"Work laptop"}""").status shouldBe HttpStatusCode.NoContent

                    devices(here).bodyAsText() shouldContain "\"name\":\"Work laptop\""
                }
            }

            "refuses an empty name as a field error and leaves the device as it was" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()
                    val id = idsOf(here, current = true).single()

                    val answer = name(id, here, """{"name":"   "}""")

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain "name-invalid"
                    devices(here).bodyAsText() shouldNotContain "\"name\""
                }
            }

            "refuses an act that does not come from the application's own page" {
                testApplication {
                    val served = Served()
                    application { served.api().install(this) }
                    val here = served.harness.signedIn()

                    client.delete("/api/v1/other-devices") {
                        header(HttpHeaders.Origin, "https://evil.example.test")
                        header("Idempotency-Key", nextKey())
                        withCookie("__Host-session", here)
                    }.status shouldBe HttpStatusCode.Forbidden
                }
            }
        },
    )
