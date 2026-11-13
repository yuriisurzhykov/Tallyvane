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
private const val FAST = "claude-haiku-5-5"
private const val CAREFUL = "claude-sonnet-5-5"
private const val TOKENS_A_DAY = 200_000

public class Configuration(
    public val database: DatabaseAccess,
    public val pool: Int,
    public val port: Int,
    public val level: Level,
    public val healthToken: Secret,
    public val signIn: SignInConfiguration,
    public val model: ModelConfiguration = ModelConfiguration(null, FAST, CAREFUL, TOKENS_A_DAY),
)
