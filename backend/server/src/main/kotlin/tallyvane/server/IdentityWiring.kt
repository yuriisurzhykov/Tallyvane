package tallyvane.server

import tallyvane.identity.application.IdentityRealm
import tallyvane.identity.contract.PrincipalResolver
import tallyvane.identity.infrastructure.IdentityInfrastructureFactory
import tallyvane.identity.infrastructure.PrincipalResolverFactory
import tallyvane.identity.web.routing.IdentityRoutesFactory
import tallyvane.platform.http.RequestPrincipalResolver
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.csrf.CsrfGuard
import tallyvane.platform.kernel.Clock
import tallyvane.platform.observability.log.LoggerFactory
import tallyvane.server.config.Configuration

/**
 * `identity`'s own contribution to the root, matching the shape [PlatformWiring] and every future
 * capability's own `<Feature>Wiring` share (ADR-010): what it needs in its constructor, what it
 * publishes.
 *
 * Composes the user and administrator identity realms over the shared platform pool, then publishes
 * their authentication routes, CSRF guard, and realm-aware request principal resolver.
 */
public class IdentityWiring(
    private val platform: PlatformWiring,
    private val configuration: Configuration,
    private val loggerFactory: LoggerFactory,
) {
    public val routes: List<RouteModule> by lazy {
        if (!configuration.authEnabled) {
            emptyList()
        } else {
            val infrastructure = IdentityInfrastructureFactory()
            val clock = Clock.Wall()
            val transactions = platform.persistence.transactions
            val google = configuration.google?.let { infrastructure.googleOAuth(it.clientId, it.clientSecret) }
            val delivery = infrastructure.smtpEmailDelivery(
                requireNotNull(configuration.smtpHost),
                configuration.smtpPort,
                configuration.smtpFrom,
            )
            fun useCases(realm: IdentityRealm) = infrastructure.useCases(
                transactions = transactions,
                clock = clock,
                ids = platform.ids,
                pepper = configuration.tokenPepper,
                pepperVersion = configuration.tokenPepperVersion,
                totpKeyset = requireNotNull(configuration.totpKeyset),
                totpIssuer = configuration.totpIssuer,
                accessTtl = configuration.accessTokenTtl,
                refreshIdleTtl = configuration.refreshTokenIdleTtl,
                pendingTtl = configuration.pendingAuthenticationTtl,
                attemptLimit = configuration.signInRateLimitThreshold,
                attemptWindow = configuration.signInRateLimitWindow,
                googleOAuthGateway = if (
                    realm == IdentityRealm.ADMIN && configuration.google?.adminRedirectUri == null
                ) {
                    null
                } else {
                    google
                },
                emailDelivery = delivery,
                adminEmails = configuration.adminEmails,
                realm = realm,
                loggerFactory = loggerFactory,
            )
            val userCases = useCases(IdentityRealm.USER)
            val adminCases = useCases(IdentityRealm.ADMIN)
            val bootstrap = infrastructure.bootstrapAdmins(
                transactions,
                clock,
                platform.ids,
                configuration.adminEmails,
            )
            listOf(
                IdentityRoutesFactory.Routes().routes(
                    userCases,
                    IdentityRoutesFactory.Configuration(
                        secureCookies = configuration.cookieSecure,
                        tokenLifetimes = tallyvane.identity.web.shared.SessionTokenLifetimes(
                            configuration.accessTokenTtl,
                            configuration.refreshTokenIdleTtl,
                        ),
                        googleClientId = configuration.google?.clientId,
                        googleRedirectUri = configuration.google?.redirectUri,
                        adminGoogleRedirectUri = configuration.google?.adminRedirectUri,
                    ),
                    adminCases = adminCases,
                    adminBootstrap = bootstrap,
                ),
            )
        }
    }
    public val csrfGuard: CsrfGuard? by lazy {
        if (!configuration.authEnabled) null else IdentityRoutesFactory.Routes().csrf(configuration.authOrigins)
    }

    /**
     * The generic [RequestPrincipalResolver] `platform:http`'s [tallyvane.platform.http.RequestPrincipal]
     * runs before every route — `identity`'s own [PrincipalResolver], adapted to the shape a
     * module that may never depend on `identity:contract` can still call.
     */
    public val requestPrincipal: RequestPrincipalResolver by lazy {
        val factory = PrincipalResolverFactory()
        val resolver = listOf(
            factory.resolver(
                transactions = platform.persistence.transactions,
                clock = Clock.Wall(),
                tokenPepper = configuration.tokenPepper,
                tokenPepperVersion = configuration.tokenPepperVersion,
                realm = IdentityRealm.ADMIN,
            ),
            factory.resolver(
                transactions = platform.persistence.transactions,
                clock = Clock.Wall(),
                tokenPepper = configuration.tokenPepper,
                tokenPepperVersion = configuration.tokenPepperVersion,
                realm = IdentityRealm.USER,
            ),
        )
        Adapted(resolver)
    }

    /**
     * `identity:contract.PrincipalResolver` takes a non-null cookie and answers a typed
     * [tallyvane.identity.contract.ResolvedPrincipal]; `platform:http`'s port takes a nullable one
     * and answers `Any?`, since it may not know [PrincipalResolver] exists at all. This is the one
     * place both shapes are visible, so it is the one place that bridges them.
     */
    private class Adapted(private val resolvers: List<PrincipalResolver>) : RequestPrincipalResolver {
        override suspend fun resolve(rawSessionCookie: String?): Any? {
            val raw = rawSessionCookie ?: return null
            for (resolver in resolvers) resolver.resolve(raw)?.let { return it }
            return null
        }
    }
}
