package tallyvane.authentication.infrastructure

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleConformance
import tallyvane.authentication.application.GoogleHandshake
import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Secret
import java.net.URLEncoder

private val HANDSHAKE = GoogleHandshake(Secret("state-a"), Secret("nonce-a"), Secret("verifier-a"))

private fun googleAt(stub: StubGoogle, endpoints: GoogleEndpoints = stub.endpoints()): Google = GoogleOverHttp(
    HttpClient(CIO),
    GoogleClient(StubGoogle.CLIENT_ID, StubGoogle.CLIENT_SECRET, StubGoogle.REDIRECT_URI),
    endpoints,
)

private suspend fun Google.answerTo(code: String): String = exchange(code, HANDSHAKE).reportTo(
    object : GoogleAnswer.Report<String> {
        override fun vouched(subject: String, profile: tallyvane.authentication.application.GoogleProfile): String {
            val told = mutableListOf<String>()
            profile.writeTo { name, email -> told += "$name <$email>" }
            return "vouched $subject ${told.single()}"
        }

        override fun emailUnverified(): String = "email unverified"

        override fun refused(): String = "refused"

        override fun unreachable(): String = "unreachable"
    },
)

/**
 * The adapter over HTTP, judged by the suite the fake already passes (ADR-046), against a provider of
 * its own.
 */
class GoogleOverHttpSpec : GoogleConformance() {
    private val stubs = mutableListOf<StubGoogle>()

    init {
        afterTest {
            stubs.forEach { it.close() }
            stubs.clear()
        }

        "sends the browser to Google with the PKCE challenge, the nonce and the state, and never the verifier" {
            val stub = StubGoogle().also { stubs += it }
            val challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest("verifier-a".toByteArray()),
            )

            val address = googleAt(stub).addressFor(HANDSHAKE)

            address shouldStartWith stub.endpoints().authorization
            address shouldContain "state=state-a"
            address shouldContain "nonce=nonce-a"
            address shouldContain "code_challenge=$challenge"
            address shouldContain "code_challenge_method=S256"
            address shouldContain "client_id=${StubGoogle.CLIENT_ID}"
            address shouldContain "redirect_uri=" + URLEncoder.encode(StubGoogle.REDIRECT_URI, Charsets.UTF_8)
            (address.contains("verifier-a")) shouldBe false
        }

        "refuses a token minted for another trip's nonce" {
            val stub = StubGoogle().also { stubs += it }
            stub.nonceKept = false

            googleAt(stub).answerTo(stub.codeFor(HANDSHAKE, "sub-1", "Ann", "ann@example.com", true)) shouldBe "refused"
        }

        "refuses a token minted for another client" {
            val stub = StubGoogle().also { stubs += it }
            stub.audience = "someone-elses-client"

            googleAt(stub).answerTo(stub.codeFor(HANDSHAKE, "sub-1", "Ann", "ann@example.com", true)) shouldBe "refused"
        }

        "refuses a token from another issuer" {
            val stub = StubGoogle().also { stubs += it }
            stub.issuer = "https://not-google.example"

            googleAt(stub).answerTo(stub.codeFor(HANDSHAKE, "sub-1", "Ann", "ann@example.com", true)) shouldBe "refused"
        }

        "refuses an expired token" {
            val stub = StubGoogle().also { stubs += it }
            stub.lifetimeMillis = -120_000

            googleAt(stub).answerTo(stub.codeFor(HANDSHAKE, "sub-1", "Ann", "ann@example.com", true)) shouldBe "refused"
        }

        "refuses a token signed by a key Google never published" {
            val stub = StubGoogle().also { stubs += it }
            stub.signedByImpostor = true

            googleAt(stub).answerTo(stub.codeFor(HANDSHAKE, "sub-1", "Ann", "ann@example.com", true)) shouldBe "refused"
        }

        "says Google is unreachable when its token endpoint fails" {
            val stub = StubGoogle().also { stubs += it }
            stub.tokenStatus = 503

            googleAt(stub).answerTo("any-code") shouldBe "unreachable"
        }

        "says Google is unreachable when nothing answers at its address" {
            val stub = StubGoogle().also { stubs += it }
            val dead = stub.endpoints().let {
                GoogleEndpoints(it.authorization, "http://localhost:1/token", it.keys, it.issuer)
            }

            googleAt(stub, dead).answerTo("any-code") shouldBe "unreachable"
        }
    }

    override suspend fun fresh(): Subject {
        val stub = StubGoogle().also { stubs += it }
        return object : Subject {
            override val google: Google = googleAt(stub)

            override suspend fun codeFor(
                handshake: GoogleHandshake,
                subject: String,
                name: String,
                email: String,
            ): String = stub.codeFor(handshake, subject, name, email, verified = true)

            override suspend fun unverifiedCodeFor(handshake: GoogleHandshake, subject: String): String =
                stub.codeFor(handshake, subject, "Unverified", "unverified@example.com", verified = false)
        }
    }
}
