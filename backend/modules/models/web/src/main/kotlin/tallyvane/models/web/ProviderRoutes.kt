package tallyvane.models.web

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import tallyvane.identity.contract.AccountId
import tallyvane.models.application.ModelsOutcome
import tallyvane.models.application.ProviderForm
import tallyvane.models.application.SaveProviderUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface
import kotlin.uuid.Uuid

/**
 * `PUT /model-provider/{id}`: adds the provider, or changes it. The id is the client's, so a repeat is harmless.
 */
internal class ProviderRoutes(
    private val save: SaveProviderUseCase,
    private val screen: ModelsScreen,
    private val problems: ModelsProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/model-provider")
    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.put("/{id}") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val id = call.parameters["id"]?.let(Uuid::parseOrNull) ?: Uuid.NIL
            val sent = call.receive<ProviderSent>()
            val form = ProviderForm(
                id, sent.name, sent.kind, sent.baseUrl, sent.key, sent.fast, sent.careful, sent.priceIn, sent.priceOut,
            )
            when (val outcome = save.save(AccountId(Requester(call).account()), form)) {
                is ModelsOutcome.Shown -> call.respond(screen.of(outcome))
                is ModelsOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "ProviderRoutes"
}
