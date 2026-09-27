package tallyvane.identity.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import tallyvane.identity.application.IdentityRealm
import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.application.admin.BootstrapAdminAccountsUseCase
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
import tallyvane.identity.application.secondfactor.ResetAccountMfaUseCase
import tallyvane.identity.infrastructure.email.SmtpEmailDelivery
import tallyvane.identity.infrastructure.email.SmtpSettings
import tallyvane.identity.infrastructure.google.GoogleIdTokenVerifierOverJwks
import tallyvane.identity.infrastructure.googleoauth.GoogleOAuthGatewayOverHttp
import tallyvane.identity.infrastructure.password.Argon2PasswordHasher
import tallyvane.identity.infrastructure.persistence.AuthenticationActionProofStoreOverExposed
import tallyvane.identity.infrastructure.persistence.AuthenticationPolicyAuditStoreOverExposed
import tallyvane.identity.infrastructure.persistence.AuthenticationPolicyStoreOverExposed
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
import tallyvane.platform.observability.log.LoggerFactory
import kotlin.time.Duration

/**
 * Builds real adapters once; shares the application's pool and never creates another one.
 */
public class IdentityInfrastructureFactory {
    /**
     * Builds the one-time migration that copies configured administrators into the admin realm.
     */
    public fun bootstrapAdmins(
        transactions: TransactionRunner,
        clock: Clock,
        ids: IdGenerator,
        adminEmails: Set<String>,
    ): BootstrapAdminAccountsUseCase = BootstrapAdminAccountsUseCase.Copy(
        users = UserRepositoryOverExposed(IdentityRealm.USER),
        userCredentials = CredentialRepositoryOverExposed(IdentityRealm.USER),
        userTotp = TotpEnrollmentStoreOverExposed(IdentityRealm.USER),
        userEmailMfa = EmailMfaEnrollmentStoreOverExposed(IdentityRealm.USER),
        admins = UserRepositoryOverExposed(IdentityRealm.ADMIN, adminEmails),
        adminCredentials = CredentialRepositoryOverExposed(IdentityRealm.ADMIN, adminEmails),
        adminTotp = TotpEnrollmentStoreOverExposed(IdentityRealm.ADMIN),
        adminEmailMfa = EmailMfaEnrollmentStoreOverExposed(IdentityRealm.ADMIN),
        adminEmails = adminEmails,
        ids = ids,
        clock = clock,
        transactions = transactions,
    )

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
        realm: IdentityRealm = IdentityRealm.USER,
        loggerFactory: LoggerFactory,
    ): IdentityUseCases {
        val users = UserRepositoryOverExposed(realm, adminEmails)
        val totpEnrollments: TotpEnrollmentStore = TotpEnrollmentStoreOverExposed(realm)
        val factor = SecondFactorMethod.Rfc6238(users, TinkSecretCipher(totpKeyset), totpEnrollments, clock, totpIssuer)
        val authenticationCodes = AuthenticationCodes.Hmac(pepper)
        val backupCodeStore: BackupCodeStore = BackupCodeStoreOverExposed(realm)
        val backupCodes = BackupCodes(backupCodeStore, authenticationCodes)
        val emailMfaEnrollmentStore = EmailMfaEnrollmentStoreOverExposed(realm)
        val emailChallenges = emailDelivery?.let { delivery ->
            EmailChallenges(
                EmailChallengeStoreOverExposed(realm),
                delivery,
                authenticationCodes,
                transactions,
                ids,
                clock,
            )
        }
        val authenticationPolicyStore = AuthenticationPolicyStoreOverExposed()
        val authenticationPolicyAuditStore = AuthenticationPolicyAuditStoreOverExposed(realm)
        val resetAccountMfaTarget = if (realm == IdentityRealm.ADMIN) {
            ResetAccountMfaUseCase.TargetStores(
                users = UserRepositoryOverExposed(IdentityRealm.USER),
                sessions = SessionStoreOverExposed(IdentityRealm.USER),
                refreshTokens = RefreshTokenStoreOverExposed(IdentityRealm.USER),
                pending = PendingAuthenticationStoreOverExposed(IdentityRealm.USER),
                totp = TotpEnrollmentStoreOverExposed(IdentityRealm.USER),
                emailMfa = EmailMfaEnrollmentStoreOverExposed(IdentityRealm.USER),
                backupCodes = BackupCodeStoreOverExposed(IdentityRealm.USER),
            )
        } else {
            null
        }
        val factors = buildList {
            add(factor)
            if (emailChallenges !=
                null
            ) {
                add(SecondFactorMethod.EmailOtp(users, emailMfaEnrollmentStore, emailChallenges))
            }
        }
        return IdentityUseCases(
            users, CredentialRepositoryOverExposed(realm, adminEmails),
            Argon2PasswordHasher(
                ARGON2_MEMORY_KIB,
                ARGON2_ITERATIONS,
                ARGON2_PARALLELISM,
            ),
            SessionStoreOverExposed(
                realm,
            ),
            RefreshTokenStoreOverExposed(realm), PendingAuthenticationStoreOverExposed(realm),
            factors, LoginAttemptsOverCounter(Counter.InMemory(clock)), TokenFactory.Csprng(),
            TokenHasher.Hmac(pepper, pepperVersion), transactions, clock, ids,
            accessTtl, refreshIdleTtl, pendingTtl, attemptLimit, attemptWindow, googleOAuthGateway, emailChallenges,
            backupCodes, emailMfaEnrollmentStore, authenticationPolicyStore,
            authenticationPolicyAuditStore, adminEmails, totpEnrollments, backupCodeStore,
            AuthenticationActionProofStoreOverExposed(realm),
            resetAccountMfaTarget = resetAccountMfaTarget,
            loggerFactory = loggerFactory,
        )
    }

    private companion object {
        const val ARGON2_MEMORY_KIB = 19_456
        const val ARGON2_ITERATIONS = 2
        const val ARGON2_PARALLELISM = 1
    }
}
