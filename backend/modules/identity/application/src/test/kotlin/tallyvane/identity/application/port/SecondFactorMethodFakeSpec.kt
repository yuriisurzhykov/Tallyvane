package tallyvane.identity.application.port

import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

class SecondFactorMethodFakeSpec : SecondFactorMethodConformance() {
    private val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))

    override suspend fun fresh(): SecondFactorMethod =
        SecondFactorMethodFake(SecondFactorKind.TOTP, correctCode = "424242")

    override fun userId(): UserId = userId

    override fun correctCodeFor(startEnrollmentPayload: String): String = "424242"
}
