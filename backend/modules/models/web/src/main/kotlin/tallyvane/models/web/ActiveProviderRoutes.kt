package tallyvane.models.web

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import tallyvane.identity.contract.AccountId
import tallyvane.models.application.ChooseProviderUseCase
import tallyvane.models.application.ModelsOutcome
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface
import kotlin.uuid.Uuid

internal class ActiveProviderRoutes(
    private val choose: ChooseProviderUseCase,
    private val screen: ModelsScreen,
    private val problems: ModelsProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/active-model-provider")
    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.put {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val id = Uuid.parseOrNull(call.receive<ProviderChosen>().id) ?: Uuid.NIL
            when (val outcome = choose.choose(AccountId(Requester(call).account()), id)) {
                is ModelsOutcome.Shown -> call.respond(screen.of(outcome))
                is ModelsOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "ActiveProviderRoutes"
}
