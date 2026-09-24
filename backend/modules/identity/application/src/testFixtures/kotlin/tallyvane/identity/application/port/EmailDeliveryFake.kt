package tallyvane.identity.application.port

import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

public class EmailDeliveryFake : EmailDelivery {
    public data class Message(val email: Email, val purpose: EmailChallengePurpose, val code: Secret)
    public val sent: MutableList<Message> = mutableListOf()

    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) {
        sent += Message(email, purpose, code)
    }
}
