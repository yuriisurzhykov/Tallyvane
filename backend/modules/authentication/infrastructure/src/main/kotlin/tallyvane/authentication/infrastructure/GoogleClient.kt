package tallyvane.authentication.infrastructure

import tallyvane.platform.kernel.Secret

/**
 * This application as Google knows it: the client id it was registered under, its secret, and the
 * address Google sends the browser back to, which must be one of those registered.
 */
internal class GoogleClient(private val id: String, private val secret: Secret, private val redirectUri: String) {
    init {
        require(id.isNotBlank() && redirectUri.isNotBlank()) { "Google needs a client id and a redirect address." }
    }

    fun id(): String = id

    fun secret(): String = secret.revealed()

    fun redirectUri(): String = redirectUri

    override fun toString(): String = "GoogleClient(id=$id)"
}
