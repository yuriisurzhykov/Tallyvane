package tallyvane.identity.application.port

import tallyvane.identity.domain.Credential
import tallyvane.identity.domain.UserId

/**
 * Where a [Credential] lives.
 *
 * [findPasswordFor] is scoped to the one [Credential] case this pass builds, rather than a
 * generic `findFor(userId, kind)` keyed by a discriminator nothing yet needs to name — the same
 * choice `platform:cache` made against a general value cache before `Counter` had a second thing
 * to abstract over (ADR-074). A second [Credential] case gets its own accessor when it arrives,
 * because each is read a different way, not dispatched by a shared "kind" parameter nobody has
 * asked this port to carry yet.
 */
public interface CredentialRepository {
    public suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord?

    public suspend fun save(userId: UserId, credential: Credential)
}
