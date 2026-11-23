package tallyvane.knowledge.infra

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tallyvane.knowledge.application.AnswerNotUsable
import tallyvane.knowledge.application.KnowledgeCore
import tallyvane.knowledge.application.LlmStepFailed
import tallyvane.knowledge.domain.ActionId
import tallyvane.knowledge.domain.QuestionId
import tallyvane.knowledge.domain.VacancyId
import tallyvane.knowledge.infra.llm.EmbeddingFailed
import tallyvane.knowledge.infra.llm.LlmCallFailed
import tallyvane.knowledge.infra.llm.RecordedCall
import kotlin.uuid.Uuid

@Serializable
private class AnswerRequest(val text: String, val questionId: String? = null)

@Serializable
private class MergeRequest(val newActionId: String, val existingActionId: String, val accept: Boolean)

@Serializable
private class VacancyRequest(val text: String)

@Serializable
private class ErrorResponse(val error: String, val step: String? = null)

/**
 * The HTTP face of the application: JSON in and out, one route for each use case, and a plain answer for each way
 * a use case can fail. It holds no rules; whatever is decided is decided by [core].
 */
class Api(private val core: KnowledgeCore, private val recentCalls: () -> List<RecordedCall>) {
    private val log = LoggerFactory.getLogger(Api::class.java)

    fun install(application: Application) {
        application.install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        application.install(StatusPages) {
            exception<AnswerNotUsable> { call, cause -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(cause.message.orEmpty())) }
            exception<LlmStepFailed> { call, cause -> call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message.orEmpty(), cause.step)) }
            exception<LlmCallFailed> { call, cause -> call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message.orEmpty())) }
            exception<EmbeddingFailed> { call, cause -> call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message.orEmpty())) }
            exception<BadRequestException> { call, _ ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("The request body could not be read as JSON matching this endpoint."))
            }
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("The request is not valid: ${cause.message}"))
            }
            exception<NoSuchElementException> { call, cause -> call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message.orEmpty())) }
            exception<Throwable> { call, cause ->
                log.error("A request failed.", cause)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Something went wrong. The server log has the details."))
            }
        }
        application.routing {
            staticResources("/", "static")
            post("/api/answer") {
                val request = call.receive<AnswerRequest>()
                call.respond(core.ingestAnswer.ingest(request.text, request.questionId?.let { QuestionId(Uuid.parse(it)) }))
            }
            post("/api/merge") {
                val request = call.receive<MergeRequest>()
                core.resolveMerge.resolve(ActionId(Uuid.parse(request.newActionId)), ActionId(Uuid.parse(request.existingActionId)), request.accept)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/api/question") { call.respond(core.nextQuestion.current()) }
            get("/api/graph") { call.respond(core.showGraph.show()) }
            get("/api/evidence/{id}") { call.respond(core.showEvidence.of(requireNotNull(call.parameters["id"]))) }
            post("/api/vacancy") { call.respond(core.parseVacancy.parse(call.receive<VacancyRequest>().text)) }
            get("/api/match") { call.respond(core.matchAndScore.match(vacancyOf(call.request.queryParameters["vacancyId"]))) }
            get("/api/resume") { call.respond(core.buildResume.build(vacancyOf(call.request.queryParameters["vacancyId"]))) }
            post("/api/reset") {
                core.resetData.reset()
                call.respond(HttpStatusCode.NoContent)
            }
            get("/api/debug/llm") { call.respond(recentCalls()) }
        }
    }

    private fun vacancyOf(text: String?): VacancyId =
        VacancyId(Uuid.parse(requireNotNull(text) { "vacancyId is missing." }))
}
