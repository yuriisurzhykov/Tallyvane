package tallyvane.identity.application

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
import tallyvane.identity.application.password.ChangePasswordUseCase
import tallyvane.identity.application.password.ReauthenticateUseCase
import tallyvane.identity.application.password.RegisterWithPasswordUseCase
import tallyvane.identity.application.password.SignInWithPasswordUseCase
import tallyvane.identity.application.password.VerifyPasswordUseCase
import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.LoginAttempts
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.SecondFactorMethod
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.application.secondfactor.AuthenticationPolicyAdministration
import tallyvane.identity.application.secondfactor.BeginEmailMfaEnrollmentUseCase
import tallyvane.identity.application.secondfactor.BeginRequiredFactorEnrollmentUseCase
import tallyvane.identity.application.secondfactor.ConfirmEmailMfaEnrollmentUseCase
import tallyvane.identity.application.secondfactor.ConfirmRequiredFactorEnrollmentUseCase
import tallyvane.identity.application.secondfactor.ConfirmSecondFactorEnrollmentUseCase
import tallyvane.identity.application.secondfactor.DisableSecondFactorUseCase
import tallyvane.identity.application.secondfactor.EnrollSecondFactorUseCase
import tallyvane.identity.application.secondfactor.IssueBackupCodesUseCase
import tallyvane.identity.application.secondfactor.ReadAuthenticationPolicyUseCase
import tallyvane.identity.application.secondfactor.ReadSecondFactorStatusUseCase
import tallyvane.identity.application.secondfactor.RequestEmailMfaCodeUseCase
import tallyvane.identity.application.secondfactor.ResetAccountMfaUseCase
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.application.secondfactor.UpdateAuthenticationPolicyUseCase
import tallyvane.identity.application.secondfactor.VerifySecondFactorUseCase
import tallyvane.identity.application.session.ListSessionsUseCase
import tallyvane.identity.application.session.RefreshSessionUseCase
import tallyvane.identity.application.session.RevokeAllSessionsUseCase
import tallyvane.identity.application.session.RevokeSessionUseCase
import tallyvane.identity.domain.token.RefreshRotationPolicy
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.TransactionRunner
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
) {
    private val registry = SecondFactorMethodRegistry.Default(factors)
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

    public val register: RegisterWithPasswordUseCase =
        RegisterWithPasswordUseCase.Register(users, credentials, passwords, transactions, ids, clock)
    public val signIn: SignInWithPasswordUseCase = SignInWithPasswordUseCase.RateLimited(
        SignInWithPasswordUseCase.SignIn(users, credentials, passwords, completer, transactions),
        attempts,
        attemptLimit,
        attemptWindow,
    )
    public val changePassword: ChangePasswordUseCase = ChangePasswordUseCase.Change(
        users,
        credentials,
        passwords,
        transactions,
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
        LinkGoogleAccountUseCase.Link(gateway, users, credentials, transactions)
    }
    public val unlinkGoogleAccount: UnlinkGoogleAccountUseCase = UnlinkGoogleAccountUseCase.Unlink(
        users,
        credentials,
        passwords,
        transactions,
    )
    public val readGoogleAccountLink: ReadGoogleAccountLinkUseCase = ReadGoogleAccountLinkUseCase.Read(credentials)
    public val verifyRegistrationEmail: VerifyRegistrationEmailUseCase? = emailChallenges?.let { challenges ->
        VerifyRegistrationEmailUseCase.Verify(users, challenges, transactions)
    }
    public val resendRegistrationEmail: ResendRegistrationEmailUseCase? = emailChallenges?.let { challenges ->
        ResendRegistrationEmailUseCase.Send(users, challenges, transactions)
    }
    public val requestEmailSignInCode: RequestEmailSignInCodeUseCase? = emailChallenges?.let { challenges ->
        RequestEmailSignInCodeUseCase.Issue(challenges)
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
        VerifySecondFactorUseCase.Verify(pending, registry, issuer, clock, transactions),
        attempts,
        attemptLimit,
        attemptWindow,
    )
    public val enroll: EnrollSecondFactorUseCase = EnrollSecondFactorUseCase.Enroll(registry, transactions)
    public val beginRequiredFactorEnrollment: BeginRequiredFactorEnrollmentUseCase =
        BeginRequiredFactorEnrollmentUseCase.Begin(pending, registry, clock, transactions)
    public val confirmRequiredFactorEnrollment: ConfirmRequiredFactorEnrollmentUseCase =
        ConfirmRequiredFactorEnrollmentUseCase.Confirm(pending, registry, issuer, clock, transactions)
    public val confirm: ConfirmSecondFactorEnrollmentUseCase =
        ConfirmSecondFactorEnrollmentUseCase.Confirm(registry, transactions)
    public val readSecondFactorStatus: ReadSecondFactorStatusUseCase = ReadSecondFactorStatusUseCase.Read(
        sessions,
        totpEnrollmentStore,
        emailMfaEnrollmentStore,
        backupCodeStore,
        clock,
    )
    public val disableSecondFactor: DisableSecondFactorUseCase = DisableSecondFactorUseCase.Disable(
        sessions,
        totpEnrollmentStore,
        emailMfaEnrollmentStore,
        backupCodeStore,
        authenticationPolicyStore,
        clock,
        transactions,
    )
    public val issueBackupCodes: IssueBackupCodesUseCase? = backupCodes?.let {
        IssueBackupCodesUseCase.Issue(users, credentials, passwords, it, transactions)
    }
    public val beginEmailMfaEnrollment: BeginEmailMfaEnrollmentUseCase? = emailChallenges?.let {
        BeginEmailMfaEnrollmentUseCase.Begin(users, credentials, passwords, it, transactions)
    }
    public val confirmEmailMfaEnrollment: ConfirmEmailMfaEnrollmentUseCase? = emailChallenges?.let {
        ConfirmEmailMfaEnrollmentUseCase.Confirm(users, emailMfaEnrollmentStore, it, transactions)
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
    public val resetAccountMfa: ResetAccountMfaUseCase = ResetAccountMfaUseCase.Reset(
        users, sessions, refreshTokens, pending, totpEnrollmentStore, emailMfaEnrollmentStore,
        backupCodeStore, authenticationPolicyStore, authenticationPolicyAuditStore, adminEmails,
        clock, transactions,
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
