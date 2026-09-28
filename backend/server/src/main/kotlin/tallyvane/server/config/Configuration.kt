package tallyvane.server.config

import org.slf4j.event.Level
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DatabaseAccess
import kotlin.time.Duration

/**
 * Every setting this process runs on, already known to be good.
 *
 * Everything but [google] is non-null and needs no re-checking: a value that reached this class
 * was accepted at startup by [EnvironmentConfiguration], which is where a bad one costs a failed
 * start rather than a failed request. [google] alone is allowed to be absent — see its own KDoc.
 */
public class Configuration(
    public val database: DatabaseAccess,
    public val pool: Int,
    public val port: Int,
    public val level: Level,
    public val healthToken: Secret,
    public val tokenPepper: Secret,
    public val tokenPepperVersion: Int,
    public val cookieSecure: Boolean,
    public val accessTokenTtl: Duration,
    public val refreshTokenIdleTtl: Duration,
    public val refreshTokenAbsoluteCap: Duration,
    public val pendingAuthenticationTtl: Duration,
    public val signInRateLimitThreshold: Int,
    public val signInRateLimitWindow: Duration,
    public val totpIssuer: String,
    public val google: GoogleOAuthConfig?,
)
