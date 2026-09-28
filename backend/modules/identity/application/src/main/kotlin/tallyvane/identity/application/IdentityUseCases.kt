package tallyvane.identity.application

import tallyvane.identity.application.account.AccountSettings
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.email.RequestEmailSignInCodeUseCase
import tallyvane.identity.application.email.RequestPasswordResetUseCase
import tallyvane.identity.application.email.ResendRegistrationEmailUseCase
import tallyvane.identity.application.email.ResetPasswordUseCase
import tallyvane.identity.application.email.SignInWithEmailCodeUseCase
import tallyvane.identity.application.email.VerifyRegistrationEmailUseCase
import tallyvane.identity.application.google.GoogleSignInCompleter
import tallyvane.identity.application.googleoauth.LinkGoogleAccountUseCase
import tallyvane.identity.application.googleoauth.ReadGoogleAccountLinkUseCase
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase
import tallyvane.identity.application.googleoauth.UnlinkGoogleAccountUseCase
import tallyvane.identity.application.login.ReadSignInOptionsUseCase
import tallyvane.identity.application.password.ChangePasswordUseCase
import tallyvane.identity.application.password.ReauthenticateUseCase
import tallyvane.identity.application.password.RegisterWithPasswordUseCase
import tallyvane.identity.application.password.SignInWithPasswordUseCase
import tallyvane.identity.application.password.VerifyPasswordUseCase
import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.NewSignInAlertDelivery
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SecondFactorMethod
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.application.recovery.RecoverAccountUseCase
import tallyvane.identity.application.secondfactor.AuthenticationPolicyAdministration
import tallyvane.identity.application.secondfactor.AuthorizeAuthenticationActionUseCase
import tallyvane.identity.application.secondfactor.BeginEmailMfaEnrollmentUseCase
import tallyvane.identity.application.secondfactor.ConfirmEmailMfaEnrollmentUseCase
import tallyvane.identity.application.secondfactor.ConfirmSecondFactorEnrollmentUseCase
import tallyvane.identity.application.secondfactor.DisableSecondFactorUseCase
import tallyvane.identity.application.secondfactor.EnrollSecondFactorUseCase
import tallyvane.identity.application.secondfactor.IssueBackupCodesUseCase
import tallyvane.identity.application.secondfactor.ReadAuthenticationActionSchemesUseCase
import tallyvane.identity.application.secondfactor.ReadAuthenticationPolicyUseCase
import tallyvane.identity.application.secondfactor.ReadSecondFactorStatusUseCase
import tallyvane.identity.application.secondfactor.RequestAuthenticationActionEmailCodeUseCase
import tallyvane.identity.application.secondfactor.RequestEmailMfaCodeUseCase
import tallyvane.identity.application.secondfactor.ResetAccountMfaUseCase
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.application.secondfactor.UpdateAuthenticationPolicyUseCase
import tallyvane.identity.application.secondfactor.VerifySecondFactorUseCase
import tallyvane.identity.application.session.ListSessionsUseCase
import tallyvane.identity.application.session.RefreshSessionUseCase
import tallyvane.identity.application.session.RevokeAllSessionsUseCase
import tallyvane.identity.application.session.RevokeSessionUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.token.RefreshRotationPolicy
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.observability.log.LoggerFactory
import kotlin.time.Duration

/**
 * Composes application use cases without exposing internal session issuance to adapters.
 */
public class IdentityUseCases(
    users: UserRepository,
    credentials: CredentialRepository,
    passwords: PasswordHasher,
    sessions: SessionStore,
    refreshTokens: RefreshTokenStore,
    pending: PendingAuthenticationStore,
    factors: List<SecondFactorMethod>,
    attempts: LoginAttempts,
    tokens: TokenFactory,
    hashes: TokenHasher,
    transactions: TransactionRunner,
    clock: Clock,
    ids: IdGenerator,
    accessTtl: Duration,
    refreshIdleTtl: Duration,
    pendingTtl: Duration,
    attemptLimit: Int,
    attemptWindow: Duration,
    googleOAuthGateway: GoogleOAuthGateway? = null,
    public val emailChallenges: EmailChallenges? = null,
    backupCodes: BackupCodes? = null,
    private val emailMfaEnrollmentStore: EmailMfaEnrollmentStore,
    private val authenticationPolicyStore: AuthenticationPolicyStore,
    private val authenticationPolicyAuditStore: AuthenticationPolicyAuditStore,
    adminEmails: Set<String> = emptySet(),
    private val totpEnrollmentStore: TotpEnrollmentStore,
    private val backupCodeStore: BackupCodeStore,
    private val authenticationActionProofStore: AuthenticationActionProofStore? = null,
    private val resetAccountMfaTarget: ResetAccountMfaUseCase.TargetStores? = null,
    loggerFactory: LoggerFactory = LoggerFactory(clock = clock),
    public val securityEmailDelivery: NewSignInAlertDelivery? = null,
) {
    private val registry = SecondFactorMethodRegistry.Default(factors)
    public val accountSettings: AccountSettings = AccountSettings(users, transactions)
    private val issuer = SessionIssuer.Default(
        sessions,
        refreshTokens,
        tokens,
        hashes,
        clock,
        ids,
        accessTtl,
        refreshIdleTtl,
    )
    private val completer = AuthenticationCompleter.Default(
        registry,
        pending,
        issuer,
        ids,
        clock,
        pendingTtl,
        authenticationPolicyStore,
    )
    private val actionProofRequirement = authenticationActionProofStore?.let {
        tallyvane.identity.application.secondfactor.AuthenticationActionProofRequirement(
            it,
            authenticationPolicyStore,
            sessions,
            hashes,
            clock,
        )
    }
    public val readSignInOptions: ReadSignInOptionsUseCase = ReadSignInOptionsUseCase.Read(
        authenticationPolicyStore,
        buildSet {
            add(AuthenticationTokenKind.PASSWORD)
            if (googleOAuthGateway != null) add(AuthenticationTokenKind.GOOGLE)
            if (emailChallenges != null) add(AuthenticationTokenKind.EMAIL_SIGN_IN_CODE)
        },
        transactions,
    )

    public val register: RegisterWithPasswordUseCase =
        RegisterWithPasswordUseCase.Register(users, credentials, passwords, transactions, ids, clock)
    public val signIn: SignInWithPasswordUseCase = SignInWithPasswordUseCase.RateLimited(
        SignInWithPasswordUseCase.SignIn(users, credentials, passwords, completer, transactions),
        attempts,
        attemptLimit,
        attemptWindow,
        loggerFactory.getLogger(SignInWithPasswordUseCase.RateLimited::class.java.name),
    )
    public val changePassword: ChangePasswordUseCase = ChangePasswordUseCase.Change(
        users,
        credentials,
        passwords,
        transactions,
        actionProofRequirement,
    )
    public val verifyCurrentPassword: VerifyPasswordUseCase =
        VerifyPasswordUseCase.Verify(users, credentials, passwords)
    public val reauthenticate: ReauthenticateUseCase = ReauthenticateUseCase.Reauthenticate(
        users,
        credentials,
        passwords,
        googleOAuthGateway,
        sessions,
        clock,
        transactions,
    )
    private val authenticationActionAuthorization: AuthorizeAuthenticationActionUseCase.Authorize? =
        authenticationActionProofStore?.let { proofStore ->
            AuthorizeAuthenticationActionUseCase.Authorize(
                users, credentials, passwords, googleOAuthGateway, emailChallenges, registry,
                authenticationPolicyStore, proofStore, sessions, tokens, hashes, clock, transactions,
            )
        }
    public val authorizeAuthenticationAction: AuthorizeAuthenticationActionUseCase? =
        authenticationActionAuthorization
    public val readAuthenticationActionSchemes: ReadAuthenticationActionSchemesUseCase? =
        authenticationActionAuthorization
    public val requestAuthenticationActionEmailCode: RequestAuthenticationActionEmailCodeUseCase? =
        authenticationActionAuthorization
    public val signInWithGoogleOAuth: SignInWithGoogleOAuthUseCase? = googleOAuthGateway?.let { gateway ->
        SignInWithGoogleOAuthUseCase.SignIn(
            gateway,
            GoogleSignInCompleter.Default(
                users,
                credentials,
                completer,
                transactions,
                ids,
                clock,
                authenticationPolicyStore,
            ),
        )
    }
    public val linkGoogleAccount: LinkGoogleAccountUseCase? = googleOAuthGateway?.let { gateway ->
        LinkGoogleAccountUseCase.Link(gateway, users, credentials, transactions, actionProofRequirement)
    }
    public val unlinkGoogleAccount: UnlinkGoogleAccountUseCase = UnlinkGoogleAccountUseCase.Unlink(
        users,
        credentials,
        transactions,
        actionProofRequirement,
        authenticationPolicyStore,
        factors,
        emailChallenges != null,
    )
    public val readGoogleAccountLink: ReadGoogleAccountLinkUseCase = ReadGoogleAccountLinkUseCase.Read(
        credentials,
        transactions,
    )
    public val verifyRegistrationEmail: VerifyRegistrationEmailUseCase? = emailChallenges?.let { challenges ->
        VerifyRegistrationEmailUseCase.Verify(users, challenges, transactions)
    }
    public val resendRegistrationEmail: ResendRegistrationEmailUseCase? = emailChallenges?.let { challenges ->
        ResendRegistrationEmailUseCase.Send(users, challenges, transactions)
    }
    public val requestEmailSignInCode: RequestEmailSignInCodeUseCase? = emailChallenges?.let { challenges ->
        RequestEmailSignInCodeUseCase.Issue(challenges, readSignInOptions)
    }
    public val signInWithEmailCode: SignInWithEmailCodeUseCase? = emailChallenges?.let { challenges ->
        SignInWithEmailCodeUseCase.SignIn(users, challenges, completer, transactions)
    }
    public val requestPasswordReset: RequestPasswordResetUseCase? = emailChallenges?.let { challenges ->
        RequestPasswordResetUseCase.Send(challenges)
    }
    public val resetPassword: ResetPasswordUseCase? = emailChallenges?.let { challenges ->
        ResetPasswordUseCase.Replace(users, credentials, passwords, challenges, transactions)
    }
    public val verify: VerifySecondFactorUseCase = VerifySecondFactorUseCase.RateLimited(
        VerifySecondFactorUseCase.Verify(
            pending,
            registry,
            issuer,
            clock,
            transactions,
            authenticationPolicyStore,
        ),
        attempts,
        attemptLimit,
        attemptWindow,
        loggerFactory.getLogger(VerifySecondFactorUseCase.RateLimited::class.java.name),
    )
    public val enroll: EnrollSecondFactorUseCase =
        EnrollSecondFactorUseCase.Enroll(registry, transactions, actionProofRequirement)
    public val confirm: ConfirmSecondFactorEnrollmentUseCase =
        ConfirmSecondFactorEnrollmentUseCase.Confirm(registry, transactions)
    public val readSecondFactorStatus: ReadSecondFactorStatusUseCase = ReadSecondFactorStatusUseCase.Read(
        sessions,
        totpEnrollmentStore,
        emailMfaEnrollmentStore,
        backupCodeStore,
        clock,
        transactions,
    )
    public val disableSecondFactor: DisableSecondFactorUseCase = DisableSecondFactorUseCase.Disable(
        sessions,
        totpEnrollmentStore,
        emailMfaEnrollmentStore,
        authenticationPolicyStore,
        clock,
        transactions,
        actionProofRequirement,
    )
    public val issueBackupCodes: IssueBackupCodesUseCase? = backupCodes?.let {
        IssueBackupCodesUseCase.Issue(it, transactions, actionProofRequirement)
    }
    public val recoverAccount: RecoverAccountUseCase? = backupCodes?.let {
        RecoverAccountUseCase.Recover(
            users,
            credentials,
            passwords,
            it,
            backupCodeStore,
            sessions,
            refreshTokens,
            pending,
            totpEnrollmentStore,
            emailMfaEnrollmentStore,
            issuer,
            clock,
            transactions,
        )
    }
    public val beginEmailMfaEnrollment: BeginEmailMfaEnrollmentUseCase? = emailChallenges?.let {
        BeginEmailMfaEnrollmentUseCase.Begin(users, it, transactions, actionProofRequirement)
    }
    public val confirmEmailMfaEnrollment: ConfirmEmailMfaEnrollmentUseCase? = emailChallenges?.let {
        ConfirmEmailMfaEnrollmentUseCase.Confirm(
            users,
            emailMfaEnrollmentStore,
            it,
            transactions,
        )
    }
    public val requestEmailMfaCode: RequestEmailMfaCodeUseCase? = emailChallenges?.let {
        RequestEmailMfaCodeUseCase.Request(pending, users, it, clock, transactions)
    }
    private val policyAdministration = AuthenticationPolicyAdministration(
        users,
        authenticationPolicyStore,
        authenticationPolicyAuditStore,
        adminEmails,
        clock,
        transactions,
    )
    public val readAuthenticationPolicy: ReadAuthenticationPolicyUseCase =
        ReadAuthenticationPolicyUseCase.Read(policyAdministration)
    public val updateAuthenticationPolicy: UpdateAuthenticationPolicyUseCase =
        UpdateAuthenticationPolicyUseCase.Update(policyAdministration)
    private val resetTarget = resetAccountMfaTarget ?: ResetAccountMfaUseCase.TargetStores(
        users,
        sessions,
        refreshTokens,
        pending,
        totpEnrollmentStore,
        emailMfaEnrollmentStore,
        backupCodeStore,
    )
    public val resetAccountMfa: ResetAccountMfaUseCase = ResetAccountMfaUseCase.Reset(
        resetTarget.users,
        resetTarget.sessions,
        resetTarget.refreshTokens,
        resetTarget.pending,
        resetTarget.totp,
        resetTarget.emailMfa,
        resetTarget.backupCodes,
        authenticationPolicyStore,
        authenticationPolicyAuditStore,
        adminEmails,
        clock,
        transactions,
        administrators = users,
    )
    public val refresh: RefreshSessionUseCase = RefreshSessionUseCase.Refresh(
        refreshTokens, sessions, RefreshRotationPolicy.Default(), tokens, hashes, clock,
        transactions, accessTtl, refreshIdleTtl,
    )
    public val listSessions: ListSessionsUseCase = ListSessionsUseCase.ListSessions(sessions, transactions)
    public val revoke: RevokeSessionUseCase = RevokeSessionUseCase.Revoke(sessions, refreshTokens, clock, transactions)
    public val revokeAll: RevokeAllSessionsUseCase =
        RevokeAllSessionsUseCase.RevokeAll(sessions, refreshTokens, clock, transactions)
}
