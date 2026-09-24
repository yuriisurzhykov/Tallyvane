package tallyvane.identity.web.routing

import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.EmailSignInHandler
import tallyvane.identity.web.login.RequestEmailSignInCodeHandler
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.login.SignInWithPasswordHandler
import tallyvane.identity.web.logout.LogoutAllHandler
import tallyvane.identity.web.logout.LogoutHandler
import tallyvane.identity.web.mfa.ConfirmSecondFactorEnrollmentHandler
import tallyvane.identity.web.mfa.EnrollSecondFactorHandler
import tallyvane.identity.web.mfa.SecondFactorProblems
import tallyvane.identity.web.mfa.VerifySecondFactorHandler
import tallyvane.identity.web.oauth.GoogleOAuthHandler
import tallyvane.identity.web.password.RequestPasswordResetHandler
import tallyvane.identity.web.password.ResetPasswordHandler
import tallyvane.identity.web.registration.RegisterProblems
import tallyvane.identity.web.registration.RegisterWithPasswordHandler
import tallyvane.identity.web.registration.RegistrationEmailProblems
import tallyvane.identity.web.registration.VerifyRegistrationEmailHandler
import tallyvane.identity.web.revoke.RevokeSessionHandler
import tallyvane.identity.web.session.ListSessionsHandler
import tallyvane.identity.web.session.RefreshSessionHandler
import tallyvane.identity.web.session.SessionProblems
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.identity.web.shared.SessionCookies
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.csrf.CsrfGuard

/** Public composition boundary; route implementations remain internal to the web adapter. */
interface IdentityRoutesFactory {
    fun routes(
        cases: IdentityUseCases,
        secure: Boolean,
        accessTtl: kotlin.time.Duration,
        refreshTtl: kotlin.time.Duration,
        googleClientId: String? = null,
        googleRedirectUri: String? = null,
    ): RouteModule

    fun csrf(origins: Set<String>): CsrfGuard

    class Routes : IdentityRoutesFactory {
        override fun routes(
            cases: IdentityUseCases,
            secure: Boolean,
            accessTtl: kotlin.time.Duration,
            refreshTtl: kotlin.time.Duration,
            googleClientId: String?,
            googleRedirectUri: String?,
        ): RouteModule {
            val cookies = SessionCookies.CookieJar(secure)
            val sessions = SessionProblems()
            val current = CurrentPrincipal.Resolver(sessions)
            val validation = RequestValidationProblems()
            val factors = SecondFactorProblems()
            val authenticationProblems = AuthenticationProblems()
            val signInResponses = SignInResponses.Writer(cookies, accessTtl, refreshTtl)
            val googleSignIn = cases.signInWithGoogleOAuth
            val googleOAuth = if (googleClientId != null && googleRedirectUri != null && googleSignIn != null) {
                GoogleOAuthHandler.Redirector(googleClientId, googleRedirectUri, googleSignIn, signInResponses, secure)
            } else null
            val emailChallenges =
                requireNotNull(cases.emailChallenges) { "Enabled identity routes require configured email delivery." }
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
            cases.requestEmailSignInCode?.let { requestCode ->
                handlers += RequestEmailSignInCodeHandler(
                    requestCode,
                    authenticationProblems,
                    validation,
                )
                handlers += EmailSignInHandler(requireNotNull(cases.signInWithEmailCode), signInResponses, authenticationProblems, validation)
            }
            cases.requestPasswordReset?.let { requestReset ->
                handlers += RequestPasswordResetHandler(requestReset, validation, authenticationProblems)
                handlers += ResetPasswordHandler(requireNotNull(cases.resetPassword), validation, authenticationProblems)
            }
            return AuthRoutes.Installation(
                handlers,
                secure,
                googleOAuth != null,
                googleOAuth,
                VerifyRegistrationEmailHandler.Verification(
                    verifyRegistrationEmail,
                    validation,
                    RegistrationEmailProblems(),
                ),
            )
        }

        override fun csrf(origins: Set<String>): CsrfGuard = CsrfGuard.Composite(
            listOf(
                CsrfGuard.ContentTypeGuard(),
                CsrfGuard.DoubleSubmitGuard(),
                CsrfGuard.OriginAllowlistGuard(origins),
            ),
        )
    }
}
