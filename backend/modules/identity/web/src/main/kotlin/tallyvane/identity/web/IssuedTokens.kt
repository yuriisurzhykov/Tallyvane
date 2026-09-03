package tallyvane.identity.web

import tallyvane.identity.domain.token.TokenValue
import kotlin.time.Duration

/**
 * The raw pair [SessionCookies.attach] turns into two `Set-Cookie` headers, plus the lifetime
 * each one is written with — carried as one value so a handler cannot attach one without also
 * naming how long it lives.
 */
internal data class IssuedTokens(
    val access: TokenValue,
    val accessTtl: Duration,
    val refresh: TokenValue,
    val refreshTtl: Duration,
)
