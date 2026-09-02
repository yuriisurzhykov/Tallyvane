package tallyvane.server

import tallyvane.identity.contract.PrincipalResolver
import tallyvane.identity.infrastructure.PrincipalResolverFactory
import tallyvane.platform.http.RequestPrincipalResolver
import tallyvane.platform.kernel.Clock
import tallyvane.server.config.Configuration

/**
 * `identity`'s own contribution to the root, matching the shape [PlatformWiring] and every future
 * capability's own `<Feature>Wiring` share (ADR-010): what it needs in its constructor, what it
 * publishes.
 *
 * Scoped to principal resolution only, not every port `identity` owns — nothing in `server` mounts
 * `identity`'s own routes yet, so building the rest here would have no caller.
 */
public class IdentityWiring(private val platform: PlatformWiring, private val configuration: Configuration) {
    /**
     * The generic [RequestPrincipalResolver] `platform:http`'s [tallyvane.platform.http.RequestPrincipal]
     * runs before every route — `identity`'s own [PrincipalResolver], adapted to the shape a
     * module that may never depend on `identity:contract` can still call.
     */
    public val requestPrincipal: RequestPrincipalResolver by lazy {
        val resolver = PrincipalResolverFactory().resolver(
            transactions = platform.persistence.transactions,
            clock = Clock.Wall(),
            tokenPepper = configuration.tokenPepper,
            tokenPepperVersion = configuration.tokenPepperVersion,
        )
        Adapted(resolver)
    }

    /**
     * `identity:contract.PrincipalResolver` takes a non-null cookie and answers a typed
     * [tallyvane.identity.contract.ResolvedPrincipal]; `platform:http`'s port takes a nullable one
     * and answers `Any?`, since it may not know [PrincipalResolver] exists at all. This is the one
     * place both shapes are visible, so it is the one place that bridges them.
     */
    private class Adapted(private val resolver: PrincipalResolver) : RequestPrincipalResolver {
        override suspend fun resolve(rawSessionCookie: String?): Any? = rawSessionCookie?.let { resolver.resolve(it) }
    }
}
