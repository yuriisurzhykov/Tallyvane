package tallyvane.stories.web

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.stories.application.ShowStoriesUseCase

internal class ShelfRoutes(private val show: ShowStoriesUseCase) : RouteModule {
    override val basePath: BasePath = BasePath("/stories")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val cards = StoryCards()
            show.show(Requester(call).account()).writeTo(cards)
            call.respond(StoriesShown(cards.shown()))
        }
    }

    override fun toString(): String = "ShelfRoutes"
}
