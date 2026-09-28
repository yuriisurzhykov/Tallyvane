package tallyvane.identity.application.port

import tallyvane.identity.domain.user.Email

/**
 * Optional email transport for completed new sign-ins; carries no credentials or codes.
 */
public interface NewSignInAlertDelivery {
    public suspend fun sendNewSignInAlert(email: Email, device: String)
}
