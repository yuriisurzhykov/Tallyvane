package tallyvane.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.platform.http.Access
import tallyvane.platform.http.Api
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.net.http.HttpResponse
import java.sql.DriverManager
import java.util.UUID

private const val COPIES = 6

private const val PROBE = "/api/v1/probe"

private const val BODY = """{"what":"once"}"""

private val CREATED = HttpStatusCode.Created.value

private val UNPROCESSABLE = HttpStatusCode.UnprocessableEntity.value

private val BAD_REQUEST = HttpStatusCode.BadRequest.value

private object Effects : Table("effects") {
    val n = integer("n")
}

/**
 * Something the work does that a repeat must not do again, and answers with how much has been done in
 * all, so two answers that differ show a second execution even if the table were never looked at.
 */
private class ProbeRoutes(private val transactions: TransactionRunner) : RouteModule {
    override val basePath = BasePath("/probe")

    override val access = Access.Public

    override fun install(route: Route) {
        route.post {
            val done = transactions.inTransaction {
                Effects.insert { it[n] = 1 }
                Verdict.Commit(Effects.selectAll().count())
            }
            call.respondText("""{"done":$done}""", ContentType.Application.Json, HttpStatusCode.Created)
        }
    }
}

/**
 * The real edge over the real database, with one route that writes: what a client that sends the same
 * request twice, or six times at once, can and cannot make the system do.
 */
private class Served(private val access: DatabaseAccess) : AutoCloseable {
    private val platform = PlatformWiring(settings(access, port = 0))

    val port = free()

    private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = port) {
        Api(
            routes = listOf(ProbeRoutes(platform.persistence.transactions)),
            failures = FailureTranslator.Chained(emptyList()),
            trace = TraceHeader(platform.ids),
            ledger = platform.persistence.ledger,
            callers = Callers.Anonymous(),
            appOrigin = ORIGIN,
        ).install(this)
    }.also { it.start(wait = false) }

    override fun close() {
        server.stop()
        platform.close()
    }
}

private fun effectsIn(access: DatabaseAccess): Int =
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("select count(*) from effects").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

private fun databaseWithEffects(): DatabaseAccess = PostgresFixture.migrated().also { access ->
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { it.execute("create table effects (n integer not null)") }
    }
}

private fun replayed(response: HttpResponse<String>): Boolean =
    response.headers().firstValue("Idempotent-Replayed").isPresent

private fun newKey(): String = UUID.randomUUID().toString()

class RepeatedRequestsIntegrationSpec :
    StringSpec(
        {
            "six copies of one request sent at the same moment do the work once and are answered alike" {
                val access = databaseWithEffects()
                val key = newKey()

                Served(access).use { served ->
                    val answers = (1..COPIES).map {
                        async(Dispatchers.IO) { post(served.port, PROBE, key, BODY) }
                    }.awaitAll()

                    effectsIn(access) shouldBe 1
                    answers.map { it.statusCode() }.toSet() shouldBe setOf(CREATED)
                    answers.map { it.body() }.toSet() shouldBe setOf("""{"done":1}""")
                    answers.count { replayed(it) } shouldBe COPIES - 1
                }
            }

            "a request sent again after its answer was lost gets that answer and does nothing more" {
                val access = databaseWithEffects()
                val key = newKey()

                Served(access).use { served ->
                    val first = post(served.port, PROBE, key, BODY)
                    val again = post(served.port, PROBE, key, BODY)

                    effectsIn(access) shouldBe 1
                    again.statusCode() shouldBe first.statusCode()
                    again.body() shouldBe first.body()
                    replayed(first) shouldBe false
                    replayed(again) shouldBe true
                }
            }

            "a repeat is still recognised by a server that started after the first one stopped" {
                val access = databaseWithEffects()
                val key = newKey()

                val first = Served(access).use { post(it.port, PROBE, key, BODY) }
                val again = Served(access).use { post(it.port, PROBE, key, BODY) }

                effectsIn(access) shouldBe 1
                again.body() shouldBe first.body()
                replayed(again) shouldBe true
            }

            "two different requests under two keys are both carried out" {
                val access = databaseWithEffects()

                Served(access).use { served ->
                    post(served.port, PROBE, newKey(), BODY).statusCode() shouldBe CREATED
                    post(served.port, PROBE, newKey(), BODY).statusCode() shouldBe CREATED

                    effectsIn(access) shouldBe 2
                }
            }

            "a key used again for another body is refused and does nothing" {
                val access = databaseWithEffects()
                val key = newKey()

                Served(access).use { served ->
                    post(served.port, PROBE, key, BODY)
                    val other = post(served.port, PROBE, key, """{"what":"something else"}""")

                    other.statusCode() shouldBe UNPROCESSABLE
                    effectsIn(access) shouldBe 1
                }
            }

            "a request with no key is refused before it does anything" {
                val access = databaseWithEffects()

                Served(access).use { served ->
                    post(served.port, PROBE, key = null, body = BODY).statusCode() shouldBe BAD_REQUEST

                    effectsIn(access) shouldBe 0
                }
            }
        },
    )
