package tallyvane.identity.application.port

import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.UserId

/**
 * Where a [Credential] lives.
 *
 * One accessor per [Credential] case, not a generic `findFor(userId, kind)` — why:
 * `application/README.md`.
 */
public interface CredentialRepository {
    public suspend fun findPasswordFor(userId: UserId): Credential.PasswordRecord?

    public suspend fun findGoogleFor(userId: UserId): Credential.GoogleRecord?

    /**
     * @return The id of the user [subject] already belongs to, or `null` if no account has ever
     * signed in with this Google identity before.
     */
    public suspend fun findUserIdByGoogleSubject(subject: GoogleSubject): UserId?

    /**
     * @return `true` only when the account had and removed a Google credential.
     */
    public suspend fun deleteGoogleFor(userId: UserId): Boolean

    /**
     * Claims a Google subject only when neither its subject nor the account's Google slot is
     * already occupied. The result must be safe under concurrent claims.
     */
    public suspend fun saveGoogleIfUnclaimed(userId: UserId, subject: GoogleSubject): Boolean

    public suspend fun save(userId: UserId, credential: Credential)

    /**
     * Adds a password to an account that has none or atomically replaces its current password.
     */
    public suspend fun saveOrReplacePasswordFor(userId: UserId, credential: Credential.PasswordRecord)
}
