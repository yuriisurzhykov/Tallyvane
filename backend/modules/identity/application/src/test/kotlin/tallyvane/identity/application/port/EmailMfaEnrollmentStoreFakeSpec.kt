package tallyvane.identity.application.port

import tallyvane.identity.domain.user.UserId

class EmailMfaEnrollmentStoreFakeSpec : EmailMfaEnrollmentStoreConformance() {
    override fun fresh(): EmailMfaEnrollmentStore = MemoryEmailMfaEnrollmentStore()

    private class MemoryEmailMfaEnrollmentStore : EmailMfaEnrollmentStore {
        private val users = mutableSetOf<UserId>()
        override suspend fun enroll(userId: UserId) {
            users += userId
        }
        override suspend fun unenroll(userId: UserId) {
            users -= userId
        }
        override suspend fun isEnrolled(userId: UserId): Boolean = userId in users
    }
}
