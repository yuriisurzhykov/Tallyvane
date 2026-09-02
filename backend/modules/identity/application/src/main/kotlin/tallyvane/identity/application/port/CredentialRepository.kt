package tallyvane.identity.application.port

import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.user.UserId

/**
 * Where a [Credential] lives.
 *
 * [findPasswordFor] is scoped to the one [Credential] case this pass builds, not a generic
 * `findFor(userId, kind)` — why: `application/README.md`.
 */
public interface CredentialRepository {
    public suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord?

    public suspend fun save(userId: UserId, credential: Credential)
}
