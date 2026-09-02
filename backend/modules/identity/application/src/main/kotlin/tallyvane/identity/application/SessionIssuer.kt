package tallyvane.identity.application

import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
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
import kotlin.time.Duration

/**
 * The one sequence every path that ends in a real session needs — mint a token pair, hash and
 * store both, hand the raw pair back exactly once.
 *
 * Called from every sign-in path that succeeds: password, either Google method, and a completed
 * second-factor verification. `internal` — nothing outside this module's own use cases calls it.
 *
 * Opens no transaction of its own: [tallyvane.identity.application.password.SignInWithPasswordUseCase.SignIn]
 * and every other caller are the ones that already have one open when this runs — the same rule
 * [SessionStore] and [RefreshTokenStore] state on their own KDoc, verified for real against a live
 * Postgres in `backend/playground/transactions/README.md`'s 2026-09-02 entry.
 */
internal interface SessionIssuer {
    suspend fun issue(principal: Principal, device: DeviceLabel): IssuedSession

    /**
     * [accessTokenTtl]/[refreshTokenIdleTtl] are plain constructor values, not read from
     * configuration here — the same "a number is supplied, never invented" shape
     * [tallyvane.identity.application.password.SignInWithPasswordUseCase.RateLimited] already
     * takes for its own threshold and window.
     */
    class Default(
        private val sessions: SessionStore,
        private val refreshTokens: RefreshTokenStore,
        private val tokenFactory: TokenFactory,
        private val tokenHasher: TokenHasher,
        private val clock: Clock,
        private val ids: IdGenerator,
        private val accessTokenTtl: Duration,
        private val refreshTokenIdleTtl: Duration,
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
                revokedAt = null,
            )
            sessions.save(session)
            sessions.attachAccessToken(
                session.id,
                tokenHasher.hash(tokens.access),
                now + accessTokenTtl,
                lastUsedAt = now,
            )
            refreshTokens.issueFirst(
                sessionId = session.id,
                familyId = session.tokenFamilyId,
                hash = tokenHasher.hash(tokens.refresh),
                expiresAt = now + refreshTokenIdleTtl,
                issuedAt = now,
            )
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
