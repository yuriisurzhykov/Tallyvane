package tallyvane.platform.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.call
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.util.pipeline.PipelineContext
import org.slf4j.LoggerFactory
import tallyvane.platform.idempotency.Answer
import tallyvane.platform.idempotency.Claim
import tallyvane.platform.idempotency.Ledger
import tallyvane.platform.kernel.Fallback

/**
 * The edge's other half of ADR-086: stores the answer of a request that ran under a claim, on its way
 * out and before it is sent, so a client that repeats at once finds it.
 *
 * Storing is best effort. If it fails the client still gets its answer, and a repeat is told the truth
 * (409) instead of being run again. What is not stored, and why, is in [keep].
 */
internal class KeptAnswers(private val ledger: Ledger) {
    fun install(application: Application) {
        application.sendPipeline.intercept(ApplicationSendPipeline.After) { keepAnswer(subject) }
    }

    /**
     * Stores the answer of a request that ran under a claim, or says it is not to be stored.
     */
    private suspend fun PipelineContext<Any, PipelineCall>.keepAnswer(content: Any) {
        val claim = call.attributes.getOrNull(Repeats.CLAIM)
        if (claim != null) {
            call.attributes.remove(Repeats.CLAIM)
            (content as? OutgoingContent)?.let { keep(claim, it) }
        }
    }

    private suspend fun PipelineContext<Any, PipelineCall>.keep(claim: Claim, outgoing: OutgoingContent) {
        val status = (outgoing.status ?: call.response.status() ?: HttpStatusCode.OK).value
        // A 5xx is not stored: if the work rolled back there is no claim and the repeat should run, and if
        // it committed and the response then failed, the client is better told the truth than handed a
        // 500 for something that succeeded.
        if (status < SERVER_FAULT) {
            val bytes = replayable(outgoing)?.takeUnless { carriesCredential() }
            Fallback {
                if (bytes != null) {
                    ledger.record(claim, Answer(status, outgoing.contentType?.toString(), bytes))
                } else {
                    ledger.withhold(claim)
                }
                true
            }.orRecover { failure ->
                logger.warn("The answer to a request could not be stored; a repeat will be told it is lost", failure)
                false
            }
        }
    }

    private fun PipelineContext<Any, PipelineCall>.carriesCredential(): Boolean =
        call.attributes.contains(Repeats.WITHHELD) || call.response.headers[HttpHeaders.SetCookie] != null

    private fun replayable(content: OutgoingContent): ByteArray? = when (content) {
        is OutgoingContent.ByteArrayContent -> content.bytes().takeIf { it.size <= BODY_LIMIT_BYTES }
        is OutgoingContent.NoContent -> ByteArray(0)
        else -> null
    }

    private companion object {
        const val SERVER_FAULT = 500

        val logger = LoggerFactory.getLogger(KeptAnswers::class.java)
    }
}
