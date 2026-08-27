package tallyvane.platform.http.problems

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import tallyvane.platform.http.status.Answers

/**
 * The link that translates the framework's own failures — the ones that happen before any module
 * is reached.
 *
 * Found by measurement rather than design, and the measurement is worth keeping: a request with a
 * malformed JSON body was answering **500**. Ktor throws `BadRequestException`, no module
 * recognised it, and `Unrecognised` did what it is for — turning a client's typo into "our fault",
 * logged at ERROR as though a bug had happened.
 *
 * `Api` puts this link at the head of every chain itself rather than trusting `app` to remember,
 * because these failures belong to the platform and can occur before any module's code runs.
 */
internal class TransportFailures : FailureTranslator {
    override fun Answers.translate(failure: Throwable): Problem? = when (failure) {
        // Ktor's parsing and negotiation failures all arrive as this, including a body that is not
        // the JSON its content type claims.
        is BadRequestException -> malformed(READ)
        is NotFoundException   -> missing()
        else                   -> null
    }

    private companion object {
        /**
         * Says what to fix without quoting the parser: a serializer's message names classes and
         * field paths, which is internal shape leaking into a client answer (§17).
         */
        const val READ = "The request body could not be read as JSON matching this endpoint."
    }
}
