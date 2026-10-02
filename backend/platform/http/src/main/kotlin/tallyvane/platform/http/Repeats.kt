package tallyvane.platform.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import tallyvane.platform.idempotency.Answer
import tallyvane.platform.idempotency.Claim
import tallyvane.platform.idempotency.ClaimBusy
import tallyvane.platform.idempotency.ClaimedElsewhere
import tallyvane.platform.idempotency.Earlier
import tallyvane.platform.idempotency.Fingerprint
import tallyvane.platform.idempotency.IdempotencyKey
import tallyvane.platform.idempotency.Ledger
import kotlin.time.Duration.Companion.milliseconds

internal const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

internal const val BODY_LIMIT_BYTES = 1_048_576L

internal const val BODY_LIMIT_TEXT = "1 MiB"

internal const val UNSAFE_METHODS = "POST, PUT, PATCH and DELETE"

/**
 * The edge's half of ADR-086: asks every unsafe request for its `Idempotency-Key`, finds out what is
 * already known about it, and either answers from that or lets the request run under a [Claim].
 *
 * Two interceptors do it, and both are installed by `Api` itself, so no route can opt out and no new
 * route can forget:
 *
 * 1. **Before routing**, which is this class: an unsafe request is refused without a key; with one, its
 *    body is read once (and handed on again, unchanged) to fingerprint the request, and the [Ledger] is
 *    asked about it. A request never seen runs with its claim in the coroutine context, where the
 *    transaction runner finds it. A repeat is answered from the ledger and never reaches a route.
 * 2. **On the way out**, which is [KeptAnswers]: the answer of a request that ran under a claim is
 *    stored, before it is sent, so a client that repeats at once finds it.
 *
 * A repeat that arrives while the first is still between its commit and the storing of its answer
 * waits for it for a short, fixed time: a double click should not be told "carried out, cannot be
 * repeated" about a request whose answer is a few milliseconds away.
 */
internal class Repeats(private val ledger: Ledger) {
    private val problems = IdempotencyProblems()

    fun install(application: Application) {
        application.intercept(ApplicationCallPipeline.Call) { claimBeforeRouting() }
        KeptAnswers(ledger).install(application)
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.claimBeforeRouting() {
        if (call.request.httpMethod in UNSAFE) {
            when (val intake = intakeOf(call)) {
                is Intake.Turned -> refuse(intake.failure)
                is Intake.Admitted -> admit(intake)
            }
        }
    }

    /**
     * Reads what the claim is made of: the key, and the body, which is read here once and is the
     * reason this is a function of its own.
     */
    private suspend fun intakeOf(call: PipelineCall): Intake {
        val text = call.request.headers[IDEMPOTENCY_KEY_HEADER]
        val key = text?.let(IdempotencyKey::parse)
        val body = key?.let { bodyOf(call) }
        return when {
            text == null -> Intake.Turned(IdempotencyFailure.Missing)
            key == null -> Intake.Turned(IdempotencyFailure.Malformed)
            body == null -> Intake.Turned(IdempotencyFailure.TooLarge)
            else -> Intake.Admitted(
                Claim(call.caller().owner(), key, Fingerprint.of(call.request.httpMethod.value, call.request.uri, body)),
                body,
            )
        }
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.admit(intake: Intake.Admitted) {
        // Handed on, so the route reads the body the fingerprint was made from.
        call.request.pipeline.intercept(ApplicationReceivePipeline.Before) {
            proceedWith(ByteReadChannel(intake.body))
        }
        when (val earlier = ledger.earlier(intake.claim)) {
            Earlier.None -> run(intake.claim)
            Earlier.Unanswered -> answerFromLedger(intake.claim)
            else -> answerWith(earlier)
        }
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.run(claim: Claim) {
        call.attributes.put(CLAIM, claim)
        try {
            withContext(claim) { proceed() }
        } catch (elsewhere: ClaimedElsewhere) {
            // Another request committed this work while this one waited. Nothing here ran.
            call.attributes.remove(CLAIM)
            logger.debug("Repeat of an Idempotency-Key whose work another request committed", elsewhere)
            answerFromLedger(claim)
        } catch (busy: ClaimBusy) {
            call.attributes.remove(CLAIM)
            logger.debug("Repeat of an Idempotency-Key still running elsewhere", busy)
            refuse(IdempotencyFailure.InProgress)
        }
    }

    /**
     * Gives a repeat what the first request left, waiting briefly for an answer that is about to be
     * stored. Waiting is bounded: past it the work is known to be done and the answer is not coming.
     */
    private suspend fun PipelineContext<Unit, PipelineCall>.answerFromLedger(claim: Claim) {
        var earlier = ledger.earlier(claim)
        for (pause in PATIENCE) {
            if (earlier != Earlier.Unanswered) {
                break
            }
            delay(pause)
            earlier = ledger.earlier(claim)
        }
        answerWith(earlier)
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.answerWith(earlier: Earlier) {
        when (earlier) {
            is Earlier.Replay -> replay(earlier)
            Earlier.Different -> refuse(IdempotencyFailure.Reused)
            Earlier.Withheld, Earlier.Unanswered -> refuse(IdempotencyFailure.Lost)
            // The work was not there to repeat: it was running, rolled back, or its day was over
            // between the questions. Asking again is right whichever it was.
            Earlier.None -> refuse(IdempotencyFailure.InProgress)
        }
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.replay(earlier: Earlier.Replay) {
        val heard = Heard().also { earlier.tell(it) }
        call.response.headers.append(REPLAYED_HEADER, "true")
        heard.respondTo(call)
        finish()
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.refuse(failure: IdempotencyFailure) {
        if (failure == IdempotencyFailure.InProgress) {
            call.response.headers.append(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS)
        }
        call.respond(Refused(failure, problems))
        finish()
    }

    private suspend fun bodyOf(call: PipelineCall): ByteArray? {
        val bytes = call.receiveChannel().readRemaining(BODY_LIMIT_BYTES + 1).readByteArray()
        return bytes.takeIf { it.size <= BODY_LIMIT_BYTES }
    }

    /**
     * What the edge found in a request when it looked for a claim in it.
     */
    private sealed interface Intake {
        class Turned(val failure: IdempotencyFailure) : Intake

        class Admitted(val claim: Claim, val body: ByteArray) : Intake
    }

    /**
     * A stored answer, heard from the [Answer] and ready to be sent as a response again.
     */
    private class Heard : Answer.Record {
        private var status = HttpStatusCode.OK
        private var contentType: ContentType? = null
        private var body = ByteArray(0)

        override fun answered(status: Int, contentType: String?, body: ByteArray) {
            this.status = HttpStatusCode.fromValue(status)
            this.contentType = contentType?.let { ContentType.parse(it) }
            this.body = body
        }

        suspend fun respondTo(call: PipelineCall) {
            call.respondBytes(body, contentType, status)
        }
    }

    internal companion object {
        val CLAIM = AttributeKey<Claim>("tallyvane.idempotency.claim")

        val WITHHELD = AttributeKey<Boolean>("tallyvane.idempotency.withheld")

        val UNSAFE = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

        /**
         * How long a repeat waits, in total about three quarters of a second, for an answer that is
         * between its commit and its storing.
         */
        private val PATIENCE =
            listOf(25.milliseconds, 50.milliseconds, 100.milliseconds, 200.milliseconds, 400.milliseconds)

        private const val REPLAYED_HEADER = "Idempotent-Replayed"

        private const val RETRY_AFTER_SECONDS = "1"

        private val logger = LoggerFactory.getLogger(Repeats::class.java)
    }
}
