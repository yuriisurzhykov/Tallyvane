package tallyvane.identity.infrastructure.email

import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

internal class RecordingEmailDelivery : EmailDelivery {
    var code: Secret = Secret("")
        private set
    var sent: Int = 0
        private set

    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) {
        this.code = code
        sent += 1
    }
}
