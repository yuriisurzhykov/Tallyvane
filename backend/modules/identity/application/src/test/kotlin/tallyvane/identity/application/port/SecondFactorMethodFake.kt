package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId

/**
 * A [SecondFactorMethod] whose enrollment and correct code are set directly by a test, standing in
 * for whichever real mechanism (TOTP, WebAuthn, Email OTP) eventually implements this port.
 */
internal class SecondFactorMethodFake(
    override val kind: SecondFactorKind,
    private val correctCode: String = "123456",
) : SecondFactorMethod {
    private val enrolledUserIds: MutableSet<UserId> = mutableSetOf()

    var enrollmentStarted: UserId? = null
        private set

    fun enroll(userId: UserId) {
        enrolledUserIds += userId
    }

    override suspend fun isEnrolledFor(userId: UserId): Boolean = userId in enrolledUserIds

    override suspend fun verify(userId: UserId, code: String): Boolean =
        userId in enrolledUserIds && code == correctCode

    override suspend fun startEnrollment(userId: UserId): String {
        enrollmentStarted = userId
        return "fake-enrollment-payload-$userId"
    }

    override suspend fun confirmEnrollment(userId: UserId, code: String): Boolean {
        val confirmed = code == correctCode
        if (confirmed) enroll(userId)
        return confirmed
    }
}
