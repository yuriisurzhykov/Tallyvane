package tallyvane.identity.application.port

import tallyvane.identity.domain.user.UserId

/**
 * Stores explicit opt-in to email as a second factor; a verified address alone is not enrollment.
 */
public interface EmailMfaEnrollmentStore {
    public suspend fun enroll(userId: UserId)

    public suspend fun unenroll(userId: UserId)

    public suspend fun isEnrolled(userId: UserId): Boolean
}
