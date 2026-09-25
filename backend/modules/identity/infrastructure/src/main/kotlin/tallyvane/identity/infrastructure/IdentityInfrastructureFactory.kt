package tallyvane.identity.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.SecondFactorMethod
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.infrastructure.email.SmtpEmailDelivery
import tallyvane.identity.infrastructure.email.SmtpSettings
import tallyvane.identity.infrastructure.google.GoogleIdTokenVerifierOverJwks
import tallyvane.identity.infrastructure.googleoauth.GoogleOAuthGatewayOverHttp
import tallyvane.identity.infrastructure.password.Argon2PasswordHasher
import tallyvane.identity.infrastructure.persistence.AuthenticationPolicyAuditStoreOverExposed
import tallyvane.identity.infrastructure.persistence.AuthenticationPolicyStoreOverExposed
import tallyvane.identity.infrastructure.persistence.AuthenticationActionProofStoreOverExposed
import tallyvane.identity.infrastructure.persistence.BackupCodeStoreOverExposed
import tallyvane.identity.infrastructure.persistence.CredentialRepositoryOverExposed
import tallyvane.identity.infrastructure.persistence.EmailChallengeStoreOverExposed
import tallyvane.identity.infrastructure.persistence.EmailMfaEnrollmentStoreOverExposed
import tallyvane.identity.infrastructure.persistence.PendingAuthenticationStoreOverExposed
import tallyvane.identity.infrastructure.persistence.RefreshTokenStoreOverExposed
import tallyvane.identity.infrastructure.persistence.SessionStoreOverExposed
import tallyvane.identity.infrastructure.persistence.TotpEnrollmentStoreOverExposed
import tallyvane.identity.infrastructure.persistence.UserRepositoryOverExposed
import tallyvane.identity.infrastructure.secondfactor.TinkSecretCipher
import tallyvane.platform.cache.Counter
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import kotlin.time.Duration

/**
 * Builds real adapters once; shares the application's pool and never creates another one.
 */
public class IdentityInfrastructureFactory {
    public fun smtpEmailDelivery(host: String, port: Int, from: String): EmailDelivery =
        SmtpEmailDelivery(SmtpSettings(host, port, from))

    /**
     * Creates Google's code-exchange adapter with signature verification against Google's JWKS.
     */
    public fun googleOAuth(clientId: String, clientSecret: Secret): GoogleOAuthGateway {
        val verifier = GoogleIdTokenVerifierOverJwks(clientId)
        return GoogleOAuthGatewayOverHttp(HttpClient(CIO), clientId, clientSecret, verifier)
    }

    public fun useCases(
        transactions: TransactionRunner,
        clock: Clock,
        ids: IdGenerator,
        pepper: Secret,
        pepperVersion: Int,
        totpKeyset: Secret,
        totpIssuer: String,
        accessTtl: Duration,
        refreshIdleTtl: Duration,
        pendingTtl: Duration,
        attemptLimit: Int,
        attemptWindow: Duration,
        googleOAuthGateway: GoogleOAuthGateway? = null,
        emailDelivery: EmailDelivery? = null,
        adminEmails: Set<String> = emptySet(),
    ): IdentityUseCases {
        val users = UserRepositoryOverExposed()
        val totpEnrollments: TotpEnrollmentStore = TotpEnrollmentStoreOverExposed()
        val factor = SecondFactorMethod.Rfc6238(users, TinkSecretCipher(totpKeyset), totpEnrollments, clock, totpIssuer)
        val authenticationCodes = AuthenticationCodes.Hmac(pepper)
        val backupCodeStore: BackupCodeStore = BackupCodeStoreOverExposed()
        val backupCodes = BackupCodes(backupCodeStore, authenticationCodes)
        val backupFactor = SecondFactorMethod.Backup(backupCodes)
        val emailMfaEnrollmentStore = EmailMfaEnrollmentStoreOverExposed()
        val emailChallenges = emailDelivery?.let { delivery ->
            EmailChallenges(EmailChallengeStoreOverExposed(), delivery, authenticationCodes, transactions, ids, clock)
        }
        val factors = buildList {
            add(factor)
            add(backupFactor)
            if (emailChallenges !=
                null
            ) {
                add(SecondFactorMethod.EmailOtp(users, emailMfaEnrollmentStore, emailChallenges))
            }
        }
        return IdentityUseCases(
            users, CredentialRepositoryOverExposed(),
            Argon2PasswordHasher(
                ARGON2_MEMORY_KIB,
                ARGON2_ITERATIONS,
                ARGON2_PARALLELISM,
            ),
            SessionStoreOverExposed(), RefreshTokenStoreOverExposed(), PendingAuthenticationStoreOverExposed(),
            factors, LoginAttemptsOverCounter(Counter.InMemory(clock)), TokenFactory.Csprng(),
            TokenHasher.Hmac(pepper, pepperVersion), transactions, clock, ids,
            accessTtl, refreshIdleTtl, pendingTtl, attemptLimit, attemptWindow, googleOAuthGateway, emailChallenges,
            backupCodes, emailMfaEnrollmentStore, AuthenticationPolicyStoreOverExposed(),
            AuthenticationPolicyAuditStoreOverExposed(), adminEmails, totpEnrollments, backupCodeStore,
            AuthenticationActionProofStoreOverExposed(),
        )
    }

    private companion object {
        const val ARGON2_MEMORY_KIB = 19_456
        const val ARGON2_ITERATIONS = 2
        const val ARGON2_PARALLELISM = 1
    }
}
