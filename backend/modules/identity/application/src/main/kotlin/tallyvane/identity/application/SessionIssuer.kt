package tallyvane.identity.application

import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.DeviceLabel
import tallyvane.identity.domain.Session
import tallyvane.identity.domain.SessionId
import tallyvane.identity.domain.TokenFamilyId
import tallyvane.identity.domain.TokenKind
import tallyvane.identity.domain.TokenPair
import tallyvane.identity.domain.UserId
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * The one sequence every path that ends in a real session needs — mint a token pair, write the
 * session, hand the raw pair back exactly once — written once instead of once per caller.
 *
 * Password sign-in with no second factor, either Google method with no second factor, and a
 * completed second-factor verification will all end here. Without this collaborator that sequence
 * would be written four times, and the day one of the four is edited and the other three are
 * forgotten is the day they quietly disagree about what a session actually is.
 *
 * An interface, not a bare class, so the use cases that call it depend on this abstraction and
 * never name [Default] — the same shape every port in this codebase takes, so their own tests can
 * substitute a fake the day they need one instead of wiring the real ports [Default] depends on.
 *
 * `internal`: nothing outside this module's own use cases calls it directly.
 */
internal interface SessionIssuer {
    suspend fun issue(principal: Principal, device: DeviceLabel): IssuedSession

    /**
     * ### A corrected first draft: no `TokenHasher` here, not yet
     *
     * The design this class implements described the sequence as "mint a token pair, hash it,
     * write the session" and gave this collaborator a `TokenHasher` to do the hashing with.
     * Building against that literally stalled on one question neither the design nor [Session]'s
     * own field list answers: *where* a hash is written. [Session] carries no token or hash field
     * — matching the design's own description of the `identity.sessions` row, "principal
     * reference, a human-readable label, last_used_at, current token family id", nothing about a
     * token value — and a hash written nowhere is a hash computed for no reason. Validating a
     * presented access token later, and detecting a reused refresh token, both need a hash-indexed
     * lookup that is a genuinely separate storage concept from a [Session] row, and designing it
     * belongs with the persistence slice that will build [SessionStore]'s real implementation, not
     * this one. [TokenFactory] mints the raw pair this class returns; hashing and persisting it
     * for later validation is deferred, named here rather than silently dropped.
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
         * The only case [Principal] has today. `ServicePrincipal` is reserved, not implemented
         * (`identity/README.md`), so this `when` is exhaustive without an `else` — and will fail
         * to compile, on purpose, the day a second case is added, until this function says what a
         * service's session looks like.
         */
        private fun userIdOf(principal: Principal): UserId = when (principal) {
            is Principal.User -> UserId(principal.id.value)
        }
    }
}
