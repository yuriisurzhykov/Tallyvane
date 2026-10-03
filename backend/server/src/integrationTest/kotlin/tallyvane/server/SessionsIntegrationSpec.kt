package tallyvane.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.HttpStatusCode
import tallyvane.identity.application.AccountDirectory
import tallyvane.identity.contract.Registrant
import tallyvane.identity.infrastructure.IdentityStorageFactory
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.SecretGenerator
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import tallyvane.server.config.Configuration
import tallyvane.sessions.application.SessionKeys
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.UserAgent
import tallyvane.sessions.infrastructure.SessionsStorageFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val OK = HttpStatusCode.OK.value

private val NO_CONTENT = HttpStatusCode.NoContent.value

private val UNAUTHORIZED = HttpStatusCode.Unauthorized.value

private val FORBIDDEN = HttpStatusCode.Forbidden.value

private const val ME = "/api/v1/me"

private const val SESSION = "/api/v1/session"

/**
 * One request over a real socket, as a browser holding [cookie] would send it.
 */
private fun send(
    port: Int,
    method: String,
    path: String,
    cookie: String?,
    fromApp: Boolean = true,
): HttpResponse<String> = HttpClient.newBuilder().connectTimeout(10.seconds.toJavaDuration()).build().use { client ->
    val request = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
        .method(method, HttpRequest.BodyPublishers.noBody())
        .apply {
            cookie?.let { header("Cookie", it) }
            if (method != "GET") header("Idempotency-Key", UUID.randomUUID().toString())
            if (method != "GET" && fromApp) header("Origin", ORIGIN)
        }
        .build()
    client.send(request, HttpResponse.BodyHandlers.ofString())
}

/**
 * Plants what a completed sign-in leaves behind, through the adapters the process itself reads with, and
 * returns the secret the browser would hold.
 */
internal suspend fun signedUp(access: DatabaseAccess, settings: Configuration): Secret {
    val persistence = PostgresPersistence(access)
    try {
        val directory = AccountDirectory(IdentityStorageFactory().accounts(), IdGenerator.Uuid7())
        val now = Clock.Wall().now()
        persistence.transactions.inTransaction {
            directory.register(Registrant("google-1", "Ada Lovelace", "ada@example.com", now))
            Verdict.Commit(Unit)
        }
        val keys = SessionKeys(
            SecretGenerator.Csprng(),
            Digests.Hmac(settings.signIn.tokenPepper, settings.signIn.pepperVersion),
            IdGenerator.Uuid7(),
        )
        val issued = keys.issue()
        persistence.transactions.inTransaction {
            val account = checkNotNull(directory.withGoogle("google-1"))
            SessionsStorageFactory().sessions()
                .add(
                    issued.key,
                    Session.begin(
                        issued.id,
                        account.value,
                        setOf(Factor.Google),
                        ClientType.Browser,
                        UserAgent(null).device(),
                        now,
                        now,
                    ),
                )
            Verdict.Commit(Unit)
        }
        return issued.secret
    } finally {
        persistence.close()
    }
}

/**
 * The process over a real database, recognising a person by the session cookie it was given.
 */
class SessionsIntegrationSpec :
    StringSpec(
        {
            "closes a route to a request with no cookie" {
                val settings = settings(PostgresFixture.migrated())

                Application(settings).use { application ->
                    application.start()

                    val answer = send(settings.port, "GET", ME, cookie = null)

                    answer.statusCode() shouldBe UNAUTHORIZED
                    answer.body() shouldContain "sign-in-required"
                }
            }

            "tells a session nobody issued that it has ended" {
                val settings = settings(PostgresFixture.migrated())

                Application(settings).use { application ->
                    application.start()

                    val answer = send(settings.port, "GET", ME, cookie = "__Host-session=never-issued")

                    answer.statusCode() shouldBe UNAUTHORIZED
                    answer.body() shouldContain "session-expired"
                }
            }

            "tells a person who they are by the session they hold, until they sign out" {
                val access = PostgresFixture.migrated()
                val settings = settings(access)
                val cookie = "__Host-session=${signedUp(access, settings).revealed()}"

                Application(settings).use { application ->
                    application.start()

                    val me = send(settings.port, "GET", ME, cookie)
                    me.statusCode() shouldBe OK
                    me.body() shouldContain """"name":"Ada Lovelace""""

                    val out = send(settings.port, "DELETE", SESSION, cookie)
                    out.statusCode() shouldBe NO_CONTENT
                    out.headers().allValues("set-cookie").single() shouldStartWith "__Host-session=;"

                    send(settings.port, "GET", ME, cookie).statusCode() shouldBe UNAUTHORIZED
                }
            }

            "does not sign anybody out for a request that is not from the application's own page" {
                val access = PostgresFixture.migrated()
                val settings = settings(access)
                val cookie = "__Host-session=${signedUp(access, settings).revealed()}"

                Application(settings).use { application ->
                    application.start()

                    send(settings.port, "DELETE", SESSION, cookie, fromApp = false).statusCode() shouldBe FORBIDDEN

                    send(settings.port, "GET", ME, cookie).statusCode() shouldBe OK
                }
            }
        },
    )
