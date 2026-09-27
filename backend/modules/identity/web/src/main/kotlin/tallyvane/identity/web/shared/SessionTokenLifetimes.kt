package tallyvane.identity.web.shared

import kotlin.time.Duration

/**
 * Keeps the independently configured access and refresh cookie lifetimes together.
 */
public data class SessionTokenLifetimes(public val access: Duration, public val refresh: Duration)
