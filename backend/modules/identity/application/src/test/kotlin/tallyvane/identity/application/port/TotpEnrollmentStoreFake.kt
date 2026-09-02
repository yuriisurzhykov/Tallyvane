package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.user.UserId

/**
 * A [TotpEnrollmentStore] backed by an in-memory map, for a use case's test to inspect what was
 * saved without a real database.
 */
internal class TotpEnrollmentStoreFake : TotpEnrollmentStore {
    val saved: MutableMap<UserId, TotpEnrollment> = mutableMapOf()

    override suspend fun save(enrollment: TotpEnrollment) {
        saved[enrollment.userId] = enrollment
    }

    override suspend fun find(userId: UserId): TotpEnrollment? = saved[userId]
}
