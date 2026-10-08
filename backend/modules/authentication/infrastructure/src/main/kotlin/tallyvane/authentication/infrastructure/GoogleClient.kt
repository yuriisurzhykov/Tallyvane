package tallyvane.authentication.infrastructure

import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface

/**
 * This application as Google knows it: the client id it was registered under, its secret, and the
 * addresses Google sends the browser back to, one for each door (ADR-097), which must be among those
 * registered.
 */
internal class GoogleClient(
    private val id: String,
    private val secret: Secret,
    private val redirectUris: Map<Surface, String>,
) {
    init {
        require(id.isNotBlank()) { "Google needs a client id." }
        require(Surface.entries.all { redirectUris[it]?.isNotBlank() == true }) {
            "Google needs a redirect address for each of ${Surface.entries}."
        }
    }

    fun id(): String = id

    fun secret(): String = secret.revealed()

    fun redirectUri(surface: Surface): String = redirectUris.getValue(surface)

    override fun toString(): String = "GoogleClient(id=$id)"
}
