package tallyvane.identity.web.account

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import tallyvane.identity.application.account.AccountSettings
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.session.SessionFailure
import tallyvane.identity.web.session.SessionProblems
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

/**
 * The web adapter validates input; account settings owns its transaction.
 */
internal class AccountSettingsHandler(
    private val settings: AccountSettings,
    private val current: CurrentPrincipal,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.get("/account/profile") {
            val identity = current.resolve(call) ?: return@get
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val user = settings.read(identity.userId)
                ?: return@get call.respond(Refused(SessionFailure.NotAuthenticated, SessionProblems()))
            call.respond(ProfileBody.of(user))
        }
        route.put("/account/profile") {
            val identity = current.resolve(call) ?: return@put
            val body = call.receive<ProfileBody>()
            val validation = FieldValidation.Accumulator()
            val name = validation.field("displayName") {
                body.displayName?.trim()?.takeIf(String::isNotEmpty)?.also {
                    require(it.codePointCount(0, it.length) <= 80) { "Use at most 80 characters." }
                }
            }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@put
            }
            val user = settings.updateDisplayName(identity.userId, name)
                ?: return@put call.respond(Refused(SessionFailure.NotAuthenticated, SessionProblems()))
            call.respond(ProfileBody.of(user))
        }
        route.get("/account/notifications") {
            val identity = current.resolve(call) ?: return@get
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val user = settings.read(identity.userId)
                ?: return@get call.respond(Refused(SessionFailure.NotAuthenticated, SessionProblems()))
            call.respond(NotificationsBody.of(user))
        }
        route.put("/account/notifications") {
            val identity = current.resolve(call) ?: return@put
            val body = call.receive<NotificationsBody>()
            val user = settings.updateSecurityEmails(identity.userId, body.securityEmailsEnabled)
                ?: return@put call.respond(Refused(SessionFailure.NotAuthenticated, SessionProblems()))
            call.respond(NotificationsBody.of(user))
        }
    }
}
