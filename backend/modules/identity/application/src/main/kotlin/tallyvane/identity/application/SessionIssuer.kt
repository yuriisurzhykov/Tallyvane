package tallyvane.identity.application

import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenKind
import tallyvane.identity.domain.token.TokenPair
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * The one sequence every path that ends in a real session needs — mint a token pair, write the
 * session, hand the raw pair back exactly once.
 *
 * Called from every sign-in path that succeeds: password, either Google method, and a completed
 * second-factor verification. `internal` — nothing outside this module's own use cases calls it.
 */
internal interface SessionIssuer {
    suspend fun issue(principal: Principal, device: DeviceLabel): IssuedSession

    /**
     * Does not hash or persist the token pair it mints — why, and where that is deferred to:
     * `application/README.md`.
     */
    class Default(
        private val sessions: SessionStore,
        private val tokenFactory: TokenFactory,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val ids: IdGenerator,
    ) : SessionIssuer {
        override suspend fun issue(principal: Principal, device: DeviceLabel): IssuedSession {
            val tokens = TokenPair(
                access = tokenFactory.mint(TokenKind.ACCESS),
                refresh = tokenFactory.mint(TokenKind.REFRESH),
            )
            val now = clock.now()
            val session = Session(
                id = SessionId(ids.next()),
                userId = userIdOf(principal),
                device = device,
                tokenFamilyId = TokenFamilyId(ids.next()),
                createdAt = now,
                lastUsedAt = now,
            )
            transactions.inTransaction {
                sessions.save(session)
                Verdict.Commit(Unit)
            }
            return IssuedSession(session, tokens)
        }

        /**
         * The only case [Principal] has today — exhaustive without an `else`, so a second case
         * fails to compile here until this function says what a service's session looks like.
         */
        private fun userIdOf(principal: Principal): UserId = when (principal) {
            is Principal.User -> UserId(principal.id.value)
        }
    }
}
