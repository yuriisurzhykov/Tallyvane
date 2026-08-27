package tallyvane.app.config

import org.slf4j.event.Level
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DatabaseAccess

/**
 * Every setting this process runs on, already known to be good.
 *
 * The split from [EnvironmentConfiguration] is not decoration: reading needs branching — "if this
 * variable is absent, remember its name and keep going" — and `no-companion-logic` refuses a
 * companion factory that branches. So one type reads and validates, and this one only holds. The
 * gate found a conflated responsibility before the author did; ADR-010 records that.
 *
 * Nothing here is nullable and nothing needs re-checking downstream. A value that reached this
 * class has already been refused or accepted once, at startup, where a refusal costs a failed
 * deploy rather than a failed request.
 */
public class Configuration(
    public val database: DatabaseAccess,
    public val pool: Int,
    public val port: Int,
    public val level: Level,
    public val healthToken: Secret,
)
