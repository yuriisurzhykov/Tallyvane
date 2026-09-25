package tallyvane.identity.infrastructure.email

import tallyvane.platform.kernel.Secret

internal data class SmtpSettings(
    public val host: String,
    public val port: Int,
    public val from: String,
    public val username: String? = null,
    public val password: Secret? = null,
    public val startTls: Boolean = false,
) {
    init {
        require(host.isNotBlank())
        require(port in MIN_PORT..MAX_PORT)
        require((username == null) == (password == null))
    }

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
    }
}
