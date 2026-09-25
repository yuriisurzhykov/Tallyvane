package tallyvane.identity.web.routing

import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.web.admin.AuthenticationPolicyProblems
import tallyvane.identity.web.admin.ReadAuthenticationPolicyHandler
import tallyvane.identity.web.admin.ResetAccountMfaHandler
import tallyvane.identity.web.admin.UpdateAuthenticationPolicyHandler
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.EmailSignInHandler
import tallyvane.identity.web.login.RequestEmailSignInCodeHandler
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.login.SignInWithPasswordHandler
import tallyvane.identity.web.logout.LogoutAllHandler
import tallyvane.identity.web.logout.LogoutHandler
import tallyvane.identity.web.mfa.BeginEmailMfaEnrollmentHandler
import tallyvane.identity.web.mfa.ConfirmEmailMfaEnrollmentHandler
import tallyvane.identity.web.mfa.ConfirmSecondFactorEnrollmentHandler
import tallyvane.identity.web.mfa.DisableSecondFactorHandler
import tallyvane.identity.web.mfa.EnrollSecondFactorHandler
import tallyvane.identity.web.mfa.IssueBackupCodesHandler
import tallyvane.identity.web.mfa.ReadSecondFactorStatusHandler
import tallyvane.identity.web.mfa.RequestEmailMfaCodeHandler
import tallyvane.identity.web.mfa.RequiredFactorConfirmationHandler
import tallyvane.identity.web.mfa.RequiredFactorEnrollmentHandler
import tallyvane.identity.web.mfa.SecondFactorProblems
import tallyvane.identity.web.mfa.VerifySecondFactorHandler
import tallyvane.identity.web.oauth.GoogleOAuthHandler
import tallyvane.identity.web.password.ChangePasswordHandler
import tallyvane.identity.web.password.ReauthenticatePasswordHandler
import tallyvane.identity.web.password.RequestPasswordResetHandler
import tallyvane.identity.web.password.ResetPasswordHandler
import tallyvane.identity.web.registration.RegisterProblems
import tallyvane.identity.web.registration.RegisterWithPasswordHandler
import tallyvane.identity.web.registration.RegistrationEmailProblems
import tallyvane.identity.web.registration.ResendRegistrationEmailHandler
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

/**
 * Public composition boundary; route implementations remain internal to the web adapter.
 */
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
            val services = services(secure, accessTtl, refreshTtl)
            val googleSignIn = cases.signInWithGoogleOAuth
            val googleOAuth = googleOAuth(
                googleClientId,
                googleRedirectUri,
                googleSignIn,
                cases.linkGoogleAccount,
                cases.unlinkGoogleAccount,
                cases.readGoogleAccountLink,
                cases.reauthenticate,
                cases.readSecondFactorStatus,
                services,
                secure,
            )
            return AuthRoutes.Installation(
                handlers = handlers(cases, services),
                secure = secure,
                googleEnabled = googleOAuth != null,
                googleOAuth = googleOAuth,
                registrationEmailVerification = registrationEmailVerification(cases, services.validation),
            )
        }

        private data class Services(
            val cookies: SessionCookies.CookieJar,
            val sessions: SessionProblems,
            val current: CurrentPrincipal.Resolver,
            val validation: RequestValidationProblems,
            val factors: SecondFactorProblems,
            val authentication: AuthenticationProblems,
            val responses: SignInResponses.Writer,
            val accessTtl: kotlin.time.Duration,
            val refreshTtl: kotlin.time.Duration,
        )

        private fun services(
            secure: Boolean,
            accessTtl: kotlin.time.Duration,
            refreshTtl: kotlin.time.Duration,
        ): Services {
            val cookies = SessionCookies.CookieJar(secure)
            val sessions = SessionProblems()
            return Services(
                cookies,
                sessions,
                CurrentPrincipal.Resolver(sessions),
                RequestValidationProblems(),
                SecondFactorProblems(),
                AuthenticationProblems(),
                SignInResponses.Writer(cookies, accessTtl, refreshTtl),
                accessTtl,
                refreshTtl,
            )
        }

        private fun googleOAuth(
            clientId: String?,
            redirectUri: String?,
            signIn: tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase?,
            linker: tallyvane.identity.application.googleoauth.LinkGoogleAccountUseCase?,
            unlinker: tallyvane.identity.application.googleoauth.UnlinkGoogleAccountUseCase,
            linkStatus: tallyvane.identity.application.googleoauth.ReadGoogleAccountLinkUseCase,
            reauthenticate: tallyvane.identity.application.password.ReauthenticateUseCase,
            factorStatus: tallyvane.identity.application.secondfactor.ReadSecondFactorStatusUseCase,
            services: Services,
            secure: Boolean,
        ): GoogleOAuthHandler.Redirector? = if (clientId == null || redirectUri == null) {
            null
        } else if (signIn == null || linker == null) {
            null
        } else {
            GoogleOAuthHandler.Redirector(
                clientId,
                redirectUri,
                signIn,
                services.responses,
                secure,
                linker,
                tallyvane.identity.web.oauth.GoogleOAuthStateCookie.Cookie(),
                tallyvane.identity.web.oauth.GoogleOAuthCallbackResponses.Redirector(),
                services.current,
                services.authentication,
                unlinker,
                tallyvane.identity.web.oauth.GoogleAccountProblems.Table(),
                linkStatus,
                reauthenticate,
                factorStatus,
            )
        }

        private fun registrationEmailVerification(
            cases: IdentityUseCases,
            validation: RequestValidationProblems,
        ): VerifyRegistrationEmailHandler.Verification = VerifyRegistrationEmailHandler.Verification(
            requireNotNull(cases.verifyRegistrationEmail),
            validation,
            RegistrationEmailProblems(),
        )

        private fun handlers(cases: IdentityUseCases, services: Services): List<AuthHandler> = buildList {
            val emailChallenges = requireNotNull(cases.emailChallenges) {
                "Enabled identity routes require configured email delivery."
            }
            addAll(baseHandlers(cases, services, emailChallenges))
            addOptionalFactorHandlers(cases, services)
            addPolicyHandlers(cases, services)
            addEmailHandlers(cases, services)
            addPasswordResetHandlers(cases, services)
        }

        private fun baseHandlers(
            cases: IdentityUseCases,
            services: Services,
            emailChallenges: EmailChallenges,
        ): List<AuthHandler> = listOf(
            RegisterWithPasswordHandler(cases.register, RegisterProblems(), services.validation, emailChallenges),
            ChangePasswordHandler(cases.changePassword, services.current, services.validation, services.authentication),
            SignInWithPasswordHandler(cases.signIn, services.responses, services.authentication, services.validation),
            VerifySecondFactorHandler(
                cases.verify,
                services.cookies,
                services.factors,
                services.validation,
                services.accessTtl,
                services.refreshTtl,
            ),
            EnrollSecondFactorHandler(
                cases.enroll,
                services.current,
                services.factors,
                services.validation,
                cases.readSecondFactorStatus,
            ),
            ConfirmSecondFactorEnrollmentHandler(
                cases.confirm,
                services.current,
                services.factors,
                services.validation,
                cases.readSecondFactorStatus,
            ),
            RequiredFactorEnrollmentHandler(cases.beginRequiredFactorEnrollment, services.factors, services.validation),
            RequiredFactorConfirmationHandler(
                cases.confirmRequiredFactorEnrollment,
                services.factors,
                services.validation,
            ),
            RefreshSessionHandler(
                cases.refresh,
                services.cookies,
                services.sessions,
                services.accessTtl,
                services.refreshTtl,
            ),
            ListSessionsHandler(cases.listSessions, services.current),
            RevokeSessionHandler(cases.revoke, services.current, services.sessions),
            LogoutHandler(cases.revoke, services.current, services.cookies),
            LogoutAllHandler(cases.revokeAll, services.current, services.cookies),
            ReauthenticatePasswordHandler(cases.reauthenticate, services.current, services.authentication),
        )

        private fun MutableList<AuthHandler>.addOptionalFactorHandlers(cases: IdentityUseCases, services: Services) {
            add(ReadSecondFactorStatusHandler(cases.readSecondFactorStatus, services.current))
            add(DisableSecondFactorHandler(cases.disableSecondFactor, services.current, services.factors))
            cases.issueBackupCodes?.let { issue ->
                add(IssueBackupCodesHandler(issue, services.current, services.validation, services.authentication))
            }
            cases.requestEmailMfaCode?.let { request ->
                add(RequestEmailMfaCodeHandler(request, services.factors, services.validation))
            }
            cases.beginEmailMfaEnrollment?.let { begin ->
                add(
                    BeginEmailMfaEnrollmentHandler(
                        begin,
                        services.current,
                        services.authentication,
                        services.validation,
                    ),
                )
            }
            cases.confirmEmailMfaEnrollment?.let { confirm ->
                add(ConfirmEmailMfaEnrollmentHandler(confirm, services.current, services.factors, services.validation))
            }
        }

        private fun MutableList<AuthHandler>.addPolicyHandlers(cases: IdentityUseCases, services: Services) {
            val policyProblems = AuthenticationPolicyProblems()
            add(ReadAuthenticationPolicyHandler(cases.readAuthenticationPolicy, services.current, policyProblems))
            add(UpdateAuthenticationPolicyHandler(cases.updateAuthenticationPolicy, services.current, policyProblems))
            add(ResetAccountMfaHandler(cases.resetAccountMfa, services.current, policyProblems, services.validation))
        }

        private fun MutableList<AuthHandler>.addEmailHandlers(cases: IdentityUseCases, services: Services) {
            cases.resendRegistrationEmail?.let { resend ->
                add(ResendRegistrationEmailHandler(resend, services.validation))
            }
            cases.requestEmailSignInCode?.let { requestCode ->
                add(
                    RequestEmailSignInCodeHandler(
                        requestCode,
                        services.authentication,
                        services.validation,
                    ),
                )
                add(
                    EmailSignInHandler(
                        requireNotNull(cases.signInWithEmailCode),
                        services.responses,
                        services.authentication,
                        services.validation,
                    ),
                )
            }
        }

        private fun MutableList<AuthHandler>.addPasswordResetHandlers(cases: IdentityUseCases, services: Services) {
            cases.requestPasswordReset?.let { requestReset ->
                add(RequestPasswordResetHandler(requestReset, services.validation, services.authentication))
                add(
                    ResetPasswordHandler(
                        requireNotNull(cases.resetPassword),
                        services.validation,
                        services.authentication,
                    ),
                )
            }
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
