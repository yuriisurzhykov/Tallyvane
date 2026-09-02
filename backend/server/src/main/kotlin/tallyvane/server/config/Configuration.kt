package tallyvane.server.config

import org.slf4j.event.Level
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DatabaseAccess

/**
 * Every setting this process runs on, already known to be good.
 *
 * Nothing here is nullable and nothing needs re-checking: a value that reached this class was
 * accepted at startup by [EnvironmentConfiguration], which is where a bad one costs a failed start
 * rather than a failed request.
 */
public class Configuration(
    public val database: DatabaseAccess,
    public val pool: Int,
    public val port: Int,
    public val level: Level,
    public val healthToken: Secret,
    public val tokenPepper: Secret,
    public val tokenPepperVersion: Int,
)
