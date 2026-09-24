package tallyvane.identity.web

import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.web.auth.AuthenticationProblems
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.csrf.CsrfGuard

/** Public composition boundary; route implementations remain internal to the web adapter. */
public class IdentityRoutesFactory {
    public fun routes(cases: IdentityUseCases, secure: Boolean, accessTtl: kotlin.time.Duration, refreshTtl: kotlin.time.Duration, googleClientId: String? = null, googleRedirectUri: String? = null): RouteModule {
        val cookies = SessionCookies(secure)
        val sessions = SessionProblems()
        val current = CurrentPrincipal(sessions)
        val validation = RequestValidationProblems()
        val factors = SecondFactorProblems()
        val authenticationProblems = AuthenticationProblems()
        val signInResponses = SignInResponses(cookies, accessTtl, refreshTtl)
        val googleSignIn = cases.signInWithGoogleOAuth
        val googleOAuth = if (googleClientId != null && googleRedirectUri != null && googleSignIn != null) {
            GoogleOAuthHandler(googleClientId, googleRedirectUri, googleSignIn, signInResponses, secure)
        } else null
        val emailChallenges = requireNotNull(cases.emailChallenges) { "Enabled identity routes require configured email delivery." }
        val verifyRegistrationEmail = requireNotNull(cases.verifyRegistrationEmail)
        val handlers = mutableListOf<AuthHandler>(
            RegisterWithPasswordHandler(cases.register, RegisterProblems(), validation, emailChallenges),
            SignInWithPasswordHandler(cases.signIn, signInResponses, authenticationProblems, validation),
            VerifySecondFactorHandler(cases.verify, cookies, factors, validation, accessTtl, refreshTtl),
            EnrollSecondFactorHandler(cases.enroll, current, factors, validation),
            ConfirmSecondFactorEnrollmentHandler(cases.confirm, current, factors, validation),
            RefreshSessionHandler(cases.refresh, cookies, sessions, accessTtl, refreshTtl),
            ListSessionsHandler(cases.listSessions, current),
            RevokeSessionHandler(cases.revoke, current, sessions),
            LogoutHandler(cases.revoke, current, cookies),
            LogoutAllHandler(cases.revokeAll, current, cookies),
        )
        return AuthRoutes(
            handlers,
            secure,
            googleOAuth != null,
            googleOAuth,
            VerifyRegistrationEmailHandler(verifyRegistrationEmail, validation, RegistrationEmailProblems()),
        )
    }

    public fun csrf(origins: Set<String>): CsrfGuard = CsrfGuard.Composite(
        listOf(CsrfGuard.ContentTypeGuard(), CsrfGuard.DoubleSubmitGuard(), CsrfGuard.OriginAllowlistGuard(origins)),
    )
}
