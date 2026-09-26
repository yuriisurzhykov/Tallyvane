package tallyvane.identity.application.port

import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

public interface EmailDelivery {
    /**
     * Returns only after the configured SMTP server accepts the message; failure throws.
     */
    public suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret)
}
