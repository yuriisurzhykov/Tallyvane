package tallyvane.sessions.web

import io.ktor.server.application.ApplicationCall
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.Caller
import tallyvane.platform.http.Callers
import tallyvane.sessions.application.AuthenticateUseCase
import tallyvane.sessions.application.Resolution
import tallyvane.sessions.domain.SessionId

/**
 * Recognises a person by the session cookie their browser sends (ADR-079).
 */
internal class SessionCallers(private val authenticate: AuthenticateUseCase, private val session: SessionCookie) :
    Callers {
    override suspend fun of(call: ApplicationCall): Caller =
        authenticate.resolve(session.secretIn(call)).reportTo(Naming())

    override fun toString(): String = "SessionCallers"

    private class Naming : Resolution.Report<Caller> {
        override fun signedIn(account: AccountId, session: SessionId): Caller = Caller.Signed(account.value)

        override fun lapsed(): Caller = Caller.Lapsed()

        override fun anonymous(): Caller = Caller.Anonymous()
    }
}
