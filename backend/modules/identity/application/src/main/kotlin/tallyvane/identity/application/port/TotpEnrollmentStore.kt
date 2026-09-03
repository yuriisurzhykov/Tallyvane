package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.user.UserId

/**
 * Where one account's [TotpEnrollment] lives — at most one per [UserId], active or not.
 *
 * No real implementation exists yet; only a handwritten fake in this module's own tests satisfies
 * this interface until the persistence slice designs the storage shape, the same open state
 * [SessionStore] and [PendingAuthenticationStore] are already in.
 */
public interface TotpEnrollmentStore {
    /**
     * Replaces whatever [TotpEnrollment] this user already had, active or not — starting a new
     * enrollment discards an unconfirmed one, and confirming rewrites the same row with
     * `active = true` rather than inserting a second.
     */
    public suspend fun save(enrollment: TotpEnrollment)

    public suspend fun find(userId: UserId): TotpEnrollment?
}
