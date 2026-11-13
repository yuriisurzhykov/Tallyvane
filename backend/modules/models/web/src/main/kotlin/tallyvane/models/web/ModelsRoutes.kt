package tallyvane.models.web

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.contract.AccountId
import tallyvane.models.application.ModelsOutcome
import tallyvane.models.application.ShowModelsUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface

internal class ModelsRoutes(
    private val show: ShowModelsUseCase,
    private val screen: ModelsScreen,
    private val problems: ModelsProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/model-providers")
    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = show.show(AccountId(Requester(call).account()))) {
                is ModelsOutcome.Shown -> call.respond(screen.of(outcome))
                is ModelsOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "ModelsRoutes"
}
