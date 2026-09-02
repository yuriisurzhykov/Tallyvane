package tallyvane.identity.application.port

import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId

/**
 * Where a [Session] lives once [tallyvane.identity.application.SessionIssuer] has minted one — the
 * only port that knows how to find, persist, or revoke one.
 *
 * No real implementation exists yet; only a handwritten fake in this module's own tests satisfies
 * this interface until the persistence slice designs the storage shape for a token's hash.
 */
public interface SessionStore {
    public suspend fun save(session: Session)

    public suspend fun find(id: SessionId): Session?

    public suspend fun revoke(id: SessionId)

    public suspend fun revokeAllFor(userId: UserId)

    public suspend fun listFor(userId: UserId): List<Session>
}
