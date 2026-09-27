package tallyvane.identity.web.routing

import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.application.admin.BootstrapAdminAccountsUseCase
import tallyvane.identity.application.login.ReadSignInOptionsUseCase
import tallyvane.identity.web.admin.AuthenticationActionProofHandler
import tallyvane.identity.web.admin.AuthenticationPolicyProblems
import tallyvane.identity.web.admin.ReadAuthenticationActionSchemesHandler
import tallyvane.identity.web.admin.ReadAuthenticationPolicyHandler
import tallyvane.identity.web.admin.RequestAuthenticationActionEmailCodeHandler
import tallyvane.identity.web.admin.ResetAccountMfaHandler
import tallyvane.identity.web.admin.UpdateAuthenticationPolicyHandler
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.EmailSignInHandler
import tallyvane.identity.web.login.ReadSignInOptionsHandler
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
import tallyvane.identity.web.mfa.SecondFactorProblems
import tallyvane.identity.web.mfa.VerifySecondFactorHandler
import tallyvane.identity.web.oauth.GoogleOAuthConfiguration
import tallyvane.identity.web.oauth.GoogleOAuthHandler
import tallyvane.identity.web.password.ChangePasswordHandler
import tallyvane.identity.web.password.ReauthenticatePasswordHandler
import tallyvane.identity.web.password.RequestPasswordResetHandler
import tallyvane.identity.web.password.ResetPasswordHandler
import tallyvane.identity.web.recovery.RecoverAccountHandler
import tallyvane.identity.web.registration.RegisterProblems
import tallyvane.identity.web.registration.RegisterWithPasswordHandler
import tallyvane.identity.web.registration.RegistrationEmailProblems
import tallyvane.identity.web.registration.ResendRegistrationEmailHandler
import tallyvane.identity.web.registration.VerifyRegistrationEmailHandler
import tallyvane.identity.web.revoke.RevokeSessionHandler
import tallyvane.identity.web.session.ListSessionsHandler
import tallyvane.identity.web.session.ReadCurrentSessionHandler
import tallyvane.identity.web.session.RefreshSessionHandler
import tallyvane.identity.web.session.SessionProblems
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.identity.web.shared.SessionCookies
import tallyvane.identity.web.shared.SessionTokenLifetimes
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.csrf.CsrfGuard

/**
 * Public composition boundary; route implementations remain internal to the web adapter.
 */
interface IdentityRoutesFactory {
    fun routes(
        cases: IdentityUseCases,
        configuration: Configuration,
        adminCases: IdentityUseCases? = null,
        adminBootstrap: BootstrapAdminAccountsUseCase? = null,
    ): RouteModule

    fun csrf(origins: Set<String>): CsrfGuard

    data class Configuration(
        val secureCookies: Boolean,
        val tokenLifetimes: SessionTokenLifetimes,
        val googleClientId: String? = null,
        val googleRedirectUri: String? = null,
        val adminGoogleRedirectUri: String? = null,
    )

    class Routes : IdentityRoutesFactory {
        override fun routes(
            cases: IdentityUseCases,
            configuration: Configuration,
            adminCases: IdentityUseCases?,
            adminBootstrap: BootstrapAdminAccountsUseCase?,
        ): RouteModule {
            val services = services(configuration.secureCookies, configuration.tokenLifetimes)
            val adminServices = services(configuration.secureCookies, configuration.tokenLifetimes, adminRealm = true)
            val googleOAuth = googleOAuth(
                cases,
                configuration.googleClientId,
                configuration.googleRedirectUri,
                configuration.secureCookies,
                services,
                adminRealm = false,
            )
            val adminGoogleOAuth = adminCases?.let {
                googleOAuth(
                    it,
                    configuration.googleClientId,
                    configuration.adminGoogleRedirectUri,
                    configuration.secureCookies,
                    adminServices,
                    adminRealm = true,
                )
            }
            return AuthRoutes.Installation(
                handlers = handlers(cases, services, adminRealm = false),
                secure = configuration.secureCookies,
                googleEnabled = googleOAuth != null,
                googleOAuth = googleOAuth,
                registrationEmailVerification = registrationEmailVerification(cases, services.validation),
                adminHandlers = adminCases?.let { handlers(it, adminServices, adminBootstrap, adminRealm = true) }
                    .orEmpty(),
                adminGoogleOAuth = adminGoogleOAuth,
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
            val tokenLifetimes: SessionTokenLifetimes,
        )

        private fun services(
            secure: Boolean,
            tokenLifetimes: SessionTokenLifetimes,
            adminRealm: Boolean = false,
        ): Services {
            val cookies = SessionCookies.CookieJar(secure, adminRealm)
            val sessions = SessionProblems()
            return Services(
                cookies,
                sessions,
                CurrentPrincipal.Resolver(sessions, adminRealm),
                RequestValidationProblems(),
                SecondFactorProblems(),
                AuthenticationProblems(),
                SignInResponses.Writer(cookies, tokenLifetimes),
                tokenLifetimes,
            )
        }

        private fun googleOAuth(
            cases: IdentityUseCases,
            clientId: String?,
            redirectUri: String?,
            secureCookies: Boolean,
            services: Services,
            adminRealm: Boolean,
        ): GoogleOAuthHandler.Redirector? = if (
            clientId == null || redirectUri == null
        ) {
            null
        } else if (cases.signInWithGoogleOAuth == null || cases.linkGoogleAccount == null) {
            null
        } else {
            GoogleOAuthHandler.Redirector(
                GoogleOAuthConfiguration(
                    clientId,
                    redirectUri,
                    secureCookies,
                    if (adminRealm) "/api/v1/auth/admin/google" else "/api/v1/auth/google",
                ),
                cases,
                GoogleOAuthHandler.Services(services.responses, services.current, services.authentication),
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

        private fun handlers(
            cases: IdentityUseCases,
            services: Services,
            bootstrap: BootstrapAdminAccountsUseCase? = null,
            adminRealm: Boolean,
        ): List<AuthHandler> = buildList {
            val emailChallenges = requireNotNull(cases.emailChallenges) {
                "Enabled identity routes require configured email delivery."
            }
            add(
                ReadSignInOptionsHandler(
                    if (bootstrap == null) {
                        cases.readSignInOptions
                    } else {
                        ReadSignInOptionsUseCase.AfterProvisioning(bootstrap, cases.readSignInOptions)
                    },
                ),
            )
            if (!adminRealm) {
                add(
                    RegisterWithPasswordHandler(
                        cases.register,
                        RegisterProblems(),
                        services.validation,
                        emailChallenges,
                    ),
                )
                addEmailHandlers(cases, services, includeRegistrationHandlers = true)
            } else {
                addEmailHandlers(cases, services, includeRegistrationHandlers = false)
            }
            addAll(baseHandlers(cases, services))
            addOptionalFactorHandlers(cases, services)
            addPasswordResetHandlers(cases, services)
            if (cases.authorizeAuthenticationAction != null) {
                add(
                    ReadAuthenticationActionSchemesHandler(
                        requireNotNull(cases.readAuthenticationActionSchemes),
                        services.current,
                        services.validation,
                    ),
                )
                add(
                    RequestAuthenticationActionEmailCodeHandler(
                        requireNotNull(cases.requestAuthenticationActionEmailCode),
                        services.current,
                        services.validation,
                    ),
                )
                add(
                    AuthenticationActionProofHandler(
                        requireNotNull(cases.authorizeAuthenticationAction),
                        services.current,
                        services.validation,
                    ),
                )
            }
            if (adminRealm) {
                addPolicyHandlers(cases, services)
            }
            if (!adminRealm) {
                cases.recoverAccount?.let { recover ->
                    add(
                        RecoverAccountHandler(
                            recover,
                            services.responses,
                            services.authentication,
                            services.validation,
                        ),
                    )
                }
            }
        }

        private fun baseHandlers(cases: IdentityUseCases, services: Services): List<AuthHandler> = listOf(
            ChangePasswordHandler(cases.changePassword, services.current, services.validation, services.authentication),
            SignInWithPasswordHandler(cases.signIn, services.responses, services.authentication, services.validation),
            VerifySecondFactorHandler(
                cases.verify,
                services.cookies,
                services.factors,
                services.validation,
                services.tokenLifetimes,
            ),
            EnrollSecondFactorHandler(
                cases.enroll,
                services.current,
                services.factors,
                services.validation,
            ),
            ConfirmSecondFactorEnrollmentHandler(
                cases.confirm,
                services.current,
                services.factors,
                services.validation,
            ),
            RefreshSessionHandler(
                cases.refresh,
                services.cookies,
                services.sessions,
                services.tokenLifetimes,
            ),
            ReadCurrentSessionHandler(services.current),
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
                add(IssueBackupCodesHandler(issue, services.current, services.authentication))
            }
            cases.requestEmailMfaCode?.let { request ->
                add(RequestEmailMfaCodeHandler(request, services.factors, services.validation))
            }
            cases.beginEmailMfaEnrollment?.let { begin ->
                add(BeginEmailMfaEnrollmentHandler(begin, services.current))
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

        private fun MutableList<AuthHandler>.addEmailHandlers(
            cases: IdentityUseCases,
            services: Services,
            includeRegistrationHandlers: Boolean,
        ) {
            if (includeRegistrationHandlers) {
                cases.resendRegistrationEmail?.let { resend ->
                    add(ResendRegistrationEmailHandler(resend, services.validation))
                }
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
