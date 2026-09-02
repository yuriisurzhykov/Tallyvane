package tallyvane.identity.application.port

import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

/**
 * Where a [Session] lives once [tallyvane.identity.application.SessionIssuer] has minted one — the
 * only port that knows how to find, persist, or revoke one.
 *
 * Never opens a transaction of its own — [tallyvane.identity.application.SessionIssuer] and every
 * other caller are the ones that decide where a transaction begins, per `application/README.md`.
 */
public interface SessionStore {
    public suspend fun save(session: Session)

    public suspend fun find(id: SessionId): Session?

    /**
     * Marks [id] revoked without deleting it — [listFor] still shows it afterward, so an account
     * holder can see "you signed this device out" rather than have it silently vanish.
     *
     * @param revokedAt Supplied by the caller's own [tallyvane.platform.kernel.Clock], not read
     * by this store itself — every other timestamp in this module is an application-layer
     * decision, per `application/README.md`, and a store reading its own wall clock would be the
     * one exception, untestable at a fixed instant the way every other one already is.
     */
    public suspend fun revoke(id: SessionId, revokedAt: Instant)

    public suspend fun revokeAllFor(userId: UserId, revokedAt: Instant)

    public suspend fun listFor(userId: UserId): List<Session>

    /**
     * Records [hash] as [id]'s current access token, replacing whatever the previous one was, and
     * stamps [lastUsedAt] on the same row — called once when
     * [tallyvane.identity.application.SessionIssuer] first issues a session, and again on every
     * refresh, the two moments this module considers a session "used".
     *
     * @param lastUsedAt Supplied by the caller's own [tallyvane.platform.kernel.Clock], for the
     * same reason [revoke]'s [Instant] is.
     */
    public suspend fun attachAccessToken(id: SessionId, hash: HashedToken, expiresAt: Instant, lastUsedAt: Instant)

    /**
     * The session currently holding [hash] as its access token — `null` if no session has it, the
     * session is revoked, or its access token had already expired as of [now]. The one lookup
     * every protected request makes, so a real implementation answers it with a single row read,
     * no join.
     *
     * @param now Supplied by the caller's own [tallyvane.platform.kernel.Clock], for the same
     * reason [revoke]'s [Instant] is — this store never reads a wall clock of its own.
     */
    public suspend fun findByAccessTokenHash(hash: HashedToken, now: Instant): Session?
}
