package tallyvane.models.web

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import tallyvane.identity.contract.AccountId
import tallyvane.models.application.ModelsOutcome
import tallyvane.models.application.RemoveProviderUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface
import kotlin.uuid.Uuid

internal class ProviderRemovalRoutes(
    private val remove: RemoveProviderUseCase,
    private val screen: ModelsScreen,
    private val problems: ModelsProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/model-provider-removal")
    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.delete("/{id}") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val id = call.parameters["id"]?.let(Uuid::parseOrNull) ?: Uuid.NIL
            when (val outcome = remove.remove(AccountId(Requester(call).account()), id)) {
                is ModelsOutcome.Shown -> call.respond(screen.of(outcome))
                is ModelsOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "ProviderRemovalRoutes"
}
