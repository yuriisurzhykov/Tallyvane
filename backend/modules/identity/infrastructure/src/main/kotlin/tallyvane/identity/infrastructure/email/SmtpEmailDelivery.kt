package tallyvane.identity.infrastructure.email

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret
import java.util.Properties

/**
 * SMTP acceptance is synchronous. Protocol debugging is disabled to keep codes out of logs.
 */
internal class SmtpEmailDelivery(private val settings: SmtpSettings) : EmailDelivery {
    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) {
        val properties = Properties().apply {
            setProperty(smtp("host"), settings.host)
            setProperty(smtp("port"), settings.port.toString())
            setProperty(smtp("auth"), (settings.username != null).toString())
            setProperty(smtp("starttls", "enable"), settings.startTls.toString())
            setProperty(smtp("starttls", "required"), settings.startTls.toString())
            setProperty(smtp("ssl", "checkserveridentity"), "true")
            setProperty(smtp("connectiontimeout"), "5000")
            setProperty(smtp("timeout"), "5000")
            setProperty(smtp("writetimeout"), "5000")
            setProperty(listOf("mail", "debug").joinToString("."), "false")
        }
        val session = Session.getInstance(properties)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.from, true))
            setRecipient(Message.RecipientType.TO, InternetAddress(email.value, true))
            subject = "Verification code"
            setText(
                "Your code for ${purpose.name.lowercase().replace(
                    '_',
                    ' ',
                )} is ${code.revealed()}.\n\nIf you did not request this code, ignore this email.",
                "UTF-8",
            )
        }
        session.getTransport("smtp").use { transport ->
            transport.connect(settings.host, settings.port, settings.username, settings.password?.revealed())
            transport.sendMessage(message, message.allRecipients)
        }
    }

    private fun smtp(vararg key: String): String = (listOf("mail", "smtp") + key).joinToString(".")
}
