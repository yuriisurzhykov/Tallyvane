package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.Answer
import tallyvane.platform.idempotency.Claim
import tallyvane.platform.idempotency.ClaimBusy
import tallyvane.platform.idempotency.ClaimedTransactions
import tallyvane.platform.idempotency.Claims
import tallyvane.platform.idempotency.Earlier
import tallyvane.platform.idempotency.Fingerprint
import tallyvane.platform.idempotency.IdempotencyKey
import tallyvane.platform.idempotency.Ledger
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.idempotency.Owner
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val KEY_1 = "00000000-0000-0000-0000-000000000001"

private const val KEY_2 = "00000000-0000-0000-0000-000000000002"

private val PERSON_A = Uuid.parse("00000000-0000-0000-0000-0000000000a1")

private val PERSON_B = Uuid.parse("00000000-0000-0000-0000-0000000000b2")

/**
 * Routes shaped like use cases: each opens its transaction on the runner a module would be given, which
 * is the decorated one, and answers from what it did. `TransactionRunnerFake.write` stands in for a
 * write, so how many times work was *done* is a number the cases can read.
 */
private class Works(private val runner: TransactionRunnerFake, private val transactions: ClaimedTransactions) :
    RouteModule {
    override val basePath: BasePath = BasePath("/works")

    override fun install(route: Route) {
        route.get("/count") { call.respondText(runner.survivingWrites().toString()) }
        route.post("/do") {
            commit()
            call.respondText(
                """{"done":${runner.survivingWrites()}}""",
                ContentType.Application.Json,
                HttpStatusCode.Created,
            )
        }
        route.put("/do") {
            commit()
            call.respondText("put")
        }
        route.patch("/do") {
            commit()
            call.respondText("patch")
        }
        route.delete("/do") {
            commit()
            call.respondText("delete")
        }
        route.post("/echo") {
            val body = call.receiveText()
            commit()
            call.respondText(body)
        }
        route.post("/refuse") {
            transactions.inTransaction {
                runner.write()
                Verdict.Rollback(Unit)
            }
            call.respondText("refused", status = HttpStatusCode.UnprocessableEntity)
        }
        route.post("/cookie") {
            commit()
            call.response.cookies.append("session", "secret")
            call.respondText("signed in")
        }
        route.post("/token") {
            commit()
            SecretAnswer(call).withheldFromReplay()
            call.respondText("""{"token":"secret"}""", ContentType.Application.Json)
        }
        route.post("/breaks-after-commit") {
            commit()
            error("rendering failed after the work was committed")
        }
    }

    private suspend fun commit() {
        transactions.inTransaction {
            runner.write()
            Verdict.Commit(Unit)
        }
    }
}

private class Rig(
    val runner: TransactionRunnerFake = TransactionRunnerFake(),
    val ledger: LedgerFake = LedgerFake(runner, ClockFake(Instant.parse("2026-10-01T12:00:00Z"))),
    private val seen: Ledger = ledger,
    private val claims: Claims = ledger,
) {
    private val works = Works(runner, ClaimedTransactions(runner, claims))

    fun api(): Api = Api(
        routes = listOf(works),
        failures = FailureTranslator.Chained(emptyList()),
        trace = TraceHeader(IdGeneratorFake()),
        ledger = seen,
        owners = Owners { call ->
            call.request.headers["X-Person"]?.let { Owner.subject(Uuid.parse(it)) } ?: Owner.anonymous()
        },
    )
}

private fun ApplicationTestBuilder.served(rig: Rig) = application { rig.api().install(this) }

private suspend fun ApplicationTestBuilder.doing(
    key: String? = KEY_1,
    body: String = "{}",
    person: Uuid? = null,
): HttpResponse = client.post("/api/v1/works/do") {
    key?.let { header("Idempotency-Key", it) }
    person?.let { header("X-Person", it.toString()) }
    contentType(ContentType.Application.Json)
    setBody(body)
}

class RepeatsSpec :
    StringSpec(
        {
            "an unsafe request with no key is refused as malformed and does not run" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    val answer = doing(key = null)

                    answer.status shouldBe HttpStatusCode.BadRequest
                    answer.bodyAsText() shouldContain "Idempotency-Key"
                    rig.runner.survivingWrites() shouldBe 0
                }
            }

            "a key that is not a UUID is refused as malformed and does not run" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    val answer = doing(key = "not-a-uuid")

                    answer.status shouldBe HttpStatusCode.BadRequest
                    answer.bodyAsText() shouldContain "must be a UUID"
                    rig.runner.survivingWrites() shouldBe 0
                }
            }

            "a safe request needs no key" {
                testApplication {
                    served(Rig())

                    client.get("/api/v1/works/count").status shouldBe HttpStatusCode.OK
                }
            }

            "PUT, PATCH and DELETE need the key as POST does" {
                testApplication {
                    served(Rig())

                    client.put("/api/v1/works/do").status shouldBe HttpStatusCode.BadRequest
                    client.patch("/api/v1/works/do").status shouldBe HttpStatusCode.BadRequest
                    client.delete("/api/v1/works/do").status shouldBe HttpStatusCode.BadRequest
                }
            }

            "PUT, PATCH and DELETE run once with a key and answer a repeat the same" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    for ((index, send) in listOf<suspend (String) -> HttpResponse>(
                        { key -> client.put("/api/v1/works/do") { header("Idempotency-Key", key) } },
                        { key -> client.patch("/api/v1/works/do") { header("Idempotency-Key", key) } },
                        { key -> client.delete("/api/v1/works/do") { header("Idempotency-Key", key) } },
                    ).withIndex()) {
                        val key = "00000000-0000-0000-0000-00000000010$index"
                        val first = send(key).bodyAsText()
                        val repeat = send(key)

                        repeat.bodyAsText() shouldBe first
                        repeat.headers["Idempotent-Replayed"] shouldBe "true"
                    }
                    rig.runner.survivingWrites() shouldBe 3
                }
            }

            "the first request runs and is not marked as a replay" {
                testApplication {
                    served(Rig())

                    val answer = doing()

                    answer.status shouldBe HttpStatusCode.Created
                    answer.bodyAsText() shouldBe """{"done":1}"""
                    answer.headers["Idempotent-Replayed"] shouldBe null
                }
            }

            "a repeat gets the first answer again and does no work" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    val first = doing()

                    val repeat = doing()

                    repeat.status shouldBe HttpStatusCode.Created
                    repeat.bodyAsText() shouldBe first.bodyAsText()
                    repeat.headers["Content-Type"]!! shouldContain "application/json"
                    repeat.headers["Idempotent-Replayed"] shouldBe "true"
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "a repeat that sends the same key with another body is refused as a reused key" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    doing(body = """{"a":1}""")

                    val answer = doing(body = """{"a":2}""")

                    answer.status shouldBe HttpStatusCode.UnprocessableEntity
                    answer.bodyAsText() shouldContain "idempotency-key.reused"
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "two keys are two requests" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    doing(key = KEY_1)
                    doing(key = KEY_2)

                    rig.runner.survivingWrites() shouldBe 2
                }
            }

            "one person's key tells another nothing, and runs for both" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    doing(person = PERSON_A).headers["Idempotent-Replayed"] shouldBe null
                    doing(person = PERSON_B).headers["Idempotent-Replayed"] shouldBe null
                    doing(person = PERSON_A).headers["Idempotent-Replayed"] shouldBe "true"

                    rig.runner.survivingWrites() shouldBe 2
                }
            }

            "the route reads the body the fingerprint was made from" {
                testApplication {
                    served(Rig())

                    val answer = client.post("/api/v1/works/echo") {
                        header("Idempotency-Key", KEY_1)
                        setBody("""{"kept":"whole"}""")
                    }

                    answer.bodyAsText() shouldBe """{"kept":"whole"}"""
                }
            }

            "a body over the limit is refused before anything runs" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    val answer = doing(body = "x".repeat(BODY_LIMIT_BYTES.toInt() + 1))

                    answer.status shouldBe HttpStatusCode.BadRequest
                    answer.bodyAsText() shouldContain "larger than 1 MiB"
                    rig.runner.survivingWrites() shouldBe 0
                }
            }

            "a body of exactly the limit is accepted" {
                testApplication {
                    served(Rig())

                    doing(body = "x".repeat(BODY_LIMIT_BYTES.toInt())).status shouldBe HttpStatusCode.Created
                }
            }

            "a request whose work rolled back stores nothing, so its repeat runs" {
                val rig = Rig()
                testApplication {
                    served(rig)

                    val first = client.post("/api/v1/works/refuse") { header("Idempotency-Key", KEY_1) }
                    val repeat = client.post("/api/v1/works/refuse") { header("Idempotency-Key", KEY_1) }

                    first.status shouldBe HttpStatusCode.UnprocessableEntity
                    repeat.status shouldBe HttpStatusCode.UnprocessableEntity
                    repeat.headers["Idempotent-Replayed"] shouldBe null
                    rig.runner.endings.count { it == TransactionRunnerFake.Ending.RolledBack } shouldBe 2
                }
            }

            "an answer that sets a cookie is not stored, and its repeat is told so" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    client.post("/api/v1/works/cookie") { header("Idempotency-Key", KEY_1) }

                    val repeat = client.post("/api/v1/works/cookie") { header("Idempotency-Key", KEY_1) }

                    repeat.status shouldBe HttpStatusCode.Conflict
                    repeat.bodyAsText() shouldContain "cannot be given again"
                    repeat.headers["Retry-After"] shouldBe null
                    repeat.headers["Set-Cookie"] shouldBe null
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "an answer a route withholds is not stored, and its secret never reaches the ledger" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    client.post("/api/v1/works/token") { header("Idempotency-Key", KEY_1) }

                    val repeat = client.post("/api/v1/works/token") { header("Idempotency-Key", KEY_1) }

                    repeat.status shouldBe HttpStatusCode.Conflict
                    repeat.bodyAsText() shouldNotContain "secret"
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "a failure after the commit is not stored as the answer, and the repeat is told the work was done" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    val first = client.post("/api/v1/works/breaks-after-commit") { header("Idempotency-Key", KEY_1) }
                    first.status shouldBe HttpStatusCode.InternalServerError

                    val repeat = client.post("/api/v1/works/breaks-after-commit") { header("Idempotency-Key", KEY_1) }

                    repeat.status shouldBe HttpStatusCode.Conflict
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "a repeat that arrives before the answer is stored waits for it" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    rig.commitUnderClaim("""{"done":1}""")

                    val repeat = coroutineScope {
                        val asking = async { doing(body = """{"done":1}""") }
                        delay(100.milliseconds)
                        rig.ledger.record(
                            claimOfDoing("""{"done":1}"""),
                            Answer(201, "application/json", """{"done":1}""".encodeToByteArray()),
                        )
                        asking.await()
                    }

                    repeat.status shouldBe HttpStatusCode.Created
                    repeat.headers["Idempotent-Replayed"] shouldBe "true"
                }
            }

            "a repeat whose answer never comes is told the work was done, and not run again" {
                val rig = Rig()
                testApplication {
                    served(rig)
                    rig.commitUnderClaim("{}")

                    val repeat = doing()

                    repeat.status shouldBe HttpStatusCode.Conflict
                    repeat.headers["Retry-After"] shouldBe null
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "a copy that loses the race at the claim is answered from the winner's answer" {
                val rig = Rig()
                val blind = Blind(rig.ledger)
                val racing = Rig(rig.runner, rig.ledger, seen = blind)
                testApplication {
                    served(racing)
                    doing()

                    val copy = doing()

                    copy.status shouldBe HttpStatusCode.Created
                    copy.headers["Idempotent-Replayed"] shouldBe "true"
                    blind.blinded shouldBe 1
                    rig.runner.survivingWrites() shouldBe 1
                }
            }

            "a copy that waited too long for the first is told to send the same request again" {
                val rig = Rig()
                val busy = Rig(
                    rig.runner,
                    rig.ledger,
                    claims = object : Claims {
                        override fun take(claim: Claim) = throw ClaimBusy()
                    },
                )
                testApplication {
                    served(busy)

                    val answer = doing()

                    answer.status shouldBe HttpStatusCode.Conflict
                    answer.headers["Retry-After"] shouldBe "1"
                    answer.bodyAsText() shouldContain "still being processed"
                    rig.runner.survivingWrites() shouldBe 0
                }
            }
        },
    )

/**
 * Commits work under the claim the next `doing(body)` would make, without anyone answering it: the
 * state a repeat finds when the first request is between its commit and the storing of its answer.
 */
private suspend fun Rig.commitUnderClaim(body: String) {
    kotlinx.coroutines.withContext(claimOfDoing(body)) {
        ClaimedTransactions(runner, ledger).inTransaction {
            runner.write()
            Verdict.Commit(Unit)
        }
    }
}

private fun claimOfDoing(body: String): Claim = Claim(
    Owner.anonymous(),
    checkNotNull(IdempotencyKey.parse(KEY_1)),
    Fingerprint.of("POST", "/api/v1/works/do", body.encodeToByteArray()),
)

/**
 * A ledger that does not see the claim the second request asks about: what a copy in flight sees when it
 * asks before the first request has committed, though the first has committed by the time it runs.
 */
private class Blind(private val real: Ledger) : Ledger by real {
    private var asked = 0

    var blinded = 0
        private set

    override suspend fun earlier(claim: Claim): Earlier {
        asked += 1
        if (asked == THE_COPY) {
            blinded += 1
            return Earlier.None
        }
        return real.earlier(claim)
    }

    private companion object {
        /**
         * The first request asks once; the copy asks next, and is the one that does not see.
         */
        const val THE_COPY = 2
    }
}
