package tallyvane.identity.web.account

import kotlinx.serialization.Serializable
import tallyvane.identity.domain.user.User

@Serializable
internal data class ProfileBody(val displayName: String?) {
    companion object {
        fun of(user: User): ProfileBody = ProfileBody(user.displayName)
    }
}
