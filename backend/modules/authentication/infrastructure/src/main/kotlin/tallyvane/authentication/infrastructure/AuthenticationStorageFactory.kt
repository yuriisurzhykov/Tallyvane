package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.port.AccountFailures
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleHandshakes
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret

/**
 * Hands out what this module keeps authentication state in, as the ports the application layer
 * speaks to (§4.3). The adapters themselves are `internal`; the composition root names this and
 * nothing else.
 *
 * The adapters run inside a transaction the caller opened, so they hold no connection of their own
 * and building them needs no database.
 *
 * @param totpKeyset The Tink keyset (JSON) that seals TOTP seeds; the deployment's own secret. A keyset
 * that does not parse stops the server at start, which is better than at the first person's sign-in.
 */
public class AuthenticationStorageFactory(private val ids: IdGenerator, totpKeyset: Secret) {
    private val cipher: SecretCipher = TinkSecretCipher(totpKeyset)

    /**
     * Where sign-in attempts are kept.
     */
    public fun attempts(): Attempts = PostgresAttempts(AttemptRows(), ids)

    /**
     * Where the handshake of a trip to Google waits, beside the attempt it belongs to.
     */
    public fun googleHandshakes(): GoogleHandshakes = PostgresGoogleHandshakes(AttemptRows())

    /**
     * Where what Google said about a new person waits, beside the attempt it belongs to.
     */
    public fun googleProfiles(): GoogleProfiles = PostgresGoogleProfiles(AttemptRows())

    /**
     * Where the versions of each purpose's policy are kept.
     */
    public fun policyVersions(): PolicyVersions = PostgresPolicyVersions()

    /**
     * Where each account's TOTP enrolment is kept, its seed sealed.
     */
    public fun totpEnrollments(): TotpEnrollments = PostgresTotpEnrollments(cipher)

    /**
     * Where each account's recovery codes are kept.
     */
    public fun recoveryCodeSets(): RecoveryCodeSets = PostgresRecoveryCodeSets()

    /**
     * Where the wrong TOTP codes typed for each account are kept.
     */
    public fun accountFailures(): AccountFailures = PostgresAccountFailures()

    override fun toString(): String = "AuthenticationStorageFactory(schema=authentication)"
}
