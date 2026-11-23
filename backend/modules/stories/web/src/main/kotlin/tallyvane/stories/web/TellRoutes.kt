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
import tallyvane.stories.application.StoryOutcome
import tallyvane.stories.application.TellUseCase
import kotlin.uuid.Uuid

internal class TellRoutes(private val tell: TellUseCase, private val problems: StoryProblems) : RouteModule {
    override val basePath: BasePath = BasePath("/words")

    override fun install(route: Route) {
        route.post {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val telling = call.receive<Telling>()
            val story = telling.story?.let { Uuid.parseOrNull(it) ?: NOWHERE }
            when (val outcome = tell.tell(Requester(call).account(), story, telling.words)) {
                is StoryOutcome.Told -> call.respond(StoryCards().also { outcome.writeTo(it) }.shown().single())
                is StoryOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "TellRoutes"

    private companion object {
        // A story id that is not one names no story, and is answered as missing.
        val NOWHERE: Uuid = Uuid.NIL
    }
}
