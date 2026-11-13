package tallyvane.stories.web

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.stories.application.CorrectFactUseCase
import tallyvane.stories.application.StoryOutcome
import kotlin.uuid.Uuid

internal class CorrectionRoutes(private val correct: CorrectFactUseCase, private val problems: StoryProblems) :
    RouteModule {
    override val basePath: BasePath = BasePath("/corrections")

    override fun install(route: Route) {
        route.post {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val correction = call.receive<Correction>()
            val words = correction.words
            val story = Uuid.parseOrNull(correction.story) ?: Uuid.NIL
            val fact = Uuid.parseOrNull(correction.fact) ?: Uuid.NIL
            when (val outcome = correct.correct(Requester(call).account(), story, fact, words)) {
                is StoryOutcome.Told -> call.respond(StoryCards().also { outcome.writeTo(it) }.shown().single())
                is StoryOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "CorrectionRoutes"
}
