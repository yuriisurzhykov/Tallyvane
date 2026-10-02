package tallyvane.identity.infrastructure

import tallyvane.identity.application.port.KeptAccounts

/**
 * Hands out what `identity` keeps accounts in, as the port its application layer speaks to (§4.3).
 *
 * The adapter runs inside a transaction the caller opened, so it holds no connection and building it
 * needs no database.
 */
public class IdentityStorageFactory {
    /**
     * Where accounts are kept.
     */
    public fun accounts(): KeptAccounts = PostgresKeptAccounts()

    override fun toString(): String = "IdentityStorageFactory(schema=identity)"
}
