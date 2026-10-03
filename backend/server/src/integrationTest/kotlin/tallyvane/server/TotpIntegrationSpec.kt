package tallyvane.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import tallyvane.platform.persistence.PostgresFixture
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val OK = HttpStatusCode.OK.value

private val NO_CONTENT = HttpStatusCode.NoContent.value

private val CONFLICT = HttpStatusCode.Conflict.value

private val UNPROCESSABLE = HttpStatusCode.UnprocessableEntity.value

private const val CODES_TOLD = 10

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/**
 * One request over a real socket, as a browser holding [cookie] would send it, with a JSON [body] when
 * there is one.
 */
private fun request(port: Int, method: String, path: String, cookie: String, body: String? = null) =
    HttpClient.newBuilder().connectTimeout(10.seconds.toJavaDuration()).build().use { client ->
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val built = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .method(method, publisher)
            .header("Cookie", cookie)
            .apply {
                if (method != "GET") {
                    header("Origin", ORIGIN)
                    header("Idempotency-Key", UUID.randomUUID().toString())
                }
                if (body != null) header("Content-Type", "application/json")
            }
            .build()
        client.send(built, HttpResponse.BodyHandlers.ofString())
    }

/**
 * What an authenticator app shows for [key] now, written from RFC 6238 and independent of the server. Its
 * numbers are the RFC's: 30-second steps, 8-byte counters, 6 digits.
 */
@Suppress("MagicNumber")
private fun codeFor(key: String): String {
    val bits = key.map { ALPHABET.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
    val secret = bits.chunked(8).filter { it.length == 8 }.map { it.toInt(2).toByte() }.toByteArray()
    val counter = System.currentTimeMillis() / 1000 / 30
    val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
    val hash = mac.doFinal(ByteArray(8) { index -> (counter shr (56 - 8 * index)).toByte() })
    val offset = hash.last().toInt() and 0x0f
    val number = ((hash[offset].toInt() and 0x7f) shl 24) or ((hash[offset + 1].toInt() and 0xff) shl 16) or
        ((hash[offset + 2].toInt() and 0xff) shl 8) or (hash[offset + 3].toInt() and 0xff)
    return (number % 1_000_000).toString().padStart(6, '0')
}

/**
 * Turning TOTP on and off over a real socket and a real database, with the keyset the process was given.
 */
class TotpIntegrationSpec :
    StringSpec(
        {
            "a signed-in person turns TOTP on with the first code, sees it, and turns it off" {
                val access = PostgresFixture.migrated()
                val settings = settings(access)
                val cookie = "__Host-session=${signedUp(access, settings).revealed()}"

                Application(settings).use { application ->
                    application.start()
                    val port = settings.port

                    request(port, "GET", "/api/v1/second-factor", cookie).body() shouldBe
                        """{"standing":"off","recovery_codes_remaining":0}"""

                    val begun = request(port, "POST", "/api/v1/totp-enrollments", cookie)
                    begun.statusCode() shouldBe OK
                    val key = Regex(""""key":"([A-Z2-7]+)"""").find(begun.body())!!.groupValues[1]

                    val confirmed =
                        request(port, "POST", "/api/v1/totp-confirmations", cookie, """{"code":"${codeFor(key)}"}""")
                    confirmed.statusCode() shouldBe OK
                    Regex("[A-Z2-9]{5}-[A-Z2-9]{5}").findAll(confirmed.body()).count() shouldBe CODES_TOLD

                    request(port, "GET", "/api/v1/second-factor", cookie).body() shouldBe
                        """{"standing":"active","recovery_codes_remaining":10}"""

                    request(port, "POST", "/api/v1/totp-enrollments", cookie).statusCode() shouldBe CONFLICT

                    request(port, "DELETE", "/api/v1/totp-enrollment", cookie).statusCode() shouldBe NO_CONTENT
                    request(port, "GET", "/api/v1/second-factor", cookie).body() shouldContain """"standing":"off""""
                }
            }

            "a wrong first code is refused and leaves nothing on" {
                val access = PostgresFixture.migrated()
                val settings = settings(access)
                val cookie = "__Host-session=${signedUp(access, settings).revealed()}"

                Application(settings).use { application ->
                    application.start()
                    request(settings.port, "POST", "/api/v1/totp-enrollments", cookie)

                    val answer =
                        request(settings.port, "POST", "/api/v1/totp-confirmations", cookie, """{"code":"000000"}""")

                    answer.statusCode() shouldBe UNPROCESSABLE
                    request(settings.port, "GET", "/api/v1/second-factor", cookie).body() shouldContain
                        """"standing":"off""""
                }
            }
        },
    )
