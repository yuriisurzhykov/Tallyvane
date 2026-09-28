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

    /**
     * @return The id of the user [subject] already belongs to, or `null` if no account has ever
     * signed in with this Google identity before.
     */
    public suspend fun findUserIdByGoogleSubject(subject: GoogleSubject): UserId?

    public suspend fun save(userId: UserId, credential: Credential)
}
