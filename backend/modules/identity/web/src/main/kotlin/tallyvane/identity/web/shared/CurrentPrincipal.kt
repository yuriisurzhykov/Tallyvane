package tallyvane.identity.web.shared

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import tallyvane.identity.contract.Principal
import tallyvane.identity.contract.ResolvedPrincipal
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.web.session.SessionFailure
import tallyvane.identity.web.session.SessionProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RequestPrincipal

/**
 * The one place a protected route asks "who is this", by reading back what
 * [tallyvane.platform.http.RequestPrincipal.install]'s interceptor already resolved for this call
 * — never a second lookup against `identity`'s own session store, per `PrincipalResolver`'s own
 * KDoc.
 */
internal interface CurrentPrincipal {
    suspend fun resolve(call: ApplicationCall): ResolvedIdentity?
    fun peek(call: ApplicationCall): ResolvedIdentity? = null

    class Resolver(private val problems: SessionProblems, private val adminRealm: Boolean = false) : CurrentPrincipal {
        /**
         * @return [ResolvedIdentity], or `null` after already answering 401 — a caller checks for
         * `null` and returns rather than falling through to code that assumes a signed-in caller.
         */
        override suspend fun resolve(call: ApplicationCall): ResolvedIdentity? {
            val identity = peek(call)
            if (identity == null) {
                call.respond(Refused(SessionFailure.NotAuthenticated, problems))
                return null
            }
            return identity
        }

        override fun peek(call: ApplicationCall): ResolvedIdentity? {
            val resolved = RequestPrincipal.of(call) as? ResolvedPrincipal
            return resolved?.takeIf { it.principal.belongsToCurrentRealm() }?.let {
                ResolvedIdentity(UserId(it.principal.accountId()), SessionId(it.sessionId.value))
            }
        }

        private fun Principal.belongsToCurrentRealm(): Boolean = when (this) {
            is Principal.Admin -> adminRealm
            is Principal.User -> !adminRealm
        }

        private fun Principal.accountId() = when (this) {
            is Principal.Admin -> id.value
            is Principal.User -> id.value
        }
    }
}
