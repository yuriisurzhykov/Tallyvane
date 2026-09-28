package tallyvane.identity.web.account

import kotlinx.serialization.Serializable
import tallyvane.identity.domain.user.User

@Serializable
internal data class NotificationsBody(val securityEmailsEnabled: Boolean) {
    companion object {
        fun of(user: User): NotificationsBody = NotificationsBody(user.securityEmailsEnabled)
    }
}
