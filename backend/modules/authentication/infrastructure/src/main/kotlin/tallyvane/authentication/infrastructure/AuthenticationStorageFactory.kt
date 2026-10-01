package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.PolicyVersions

/**
 * Hands out what this module keeps authentication state in, as the ports the application layer
 * speaks to (§4.3). The adapters themselves are `internal`; the composition root names this and
 * nothing else.
 *
 * The adapters run inside a transaction the caller opened, so they hold no connection of their own
 * and building them needs no database.
 */
public class AuthenticationStorageFactory {
    /**
     * Where sign-in attempts are kept.
     */
    public fun attempts(): Attempts = PostgresAttempts()

    /**
     * Where the versions of each purpose's policy are kept.
     */
    public fun policyVersions(): PolicyVersions = PostgresPolicyVersions()

    override fun toString(): String = "AuthenticationStorageFactory(schema=authentication)"
}
