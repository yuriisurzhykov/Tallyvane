package tallyvane.server

import tallyvane.platform.events.EventBus
import tallyvane.platform.events.EventPublisher
import tallyvane.platform.health.HealthRoutes
import tallyvane.platform.health.ServiceToken
import tallyvane.platform.http.Api
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.observability.health.HealthCheck
import tallyvane.platform.observability.health.HealthReporter
import tallyvane.platform.persistence.MigrationsApplied
import tallyvane.platform.persistence.observability.DatabaseAnswers
import tallyvane.server.config.Configuration
import kotlin.time.Duration.Companion.seconds

/**
 * The root, and it should read as a table of contents: one line per capability, not per object.
 *
 * A capability module contributes its own `<Feature>Wiring`, which takes what it needs in its
 * constructor and publishes what it offers; this class names those and nothing else (ADR-010).
 *
 * Everything is deferred, so construction touches no database.
 */
public class Wiring(private val platform: PlatformWiring, private val configuration: Configuration) {
    /**
     * Every check the aggregate reports on, each already wrapped so that it can neither hang nor
     * throw by the time the reporter sees it (ADR-054).
     */
    public val checks: List<HealthCheck> by lazy {
        listOf(
            guarded(DatabaseAnswers(platform.persistence.transactions)),
            guarded(MigrationsApplied(platform.migrations)),
        )
    }

    private val identity = IdentityWiring(platform)

    private val authentication = AuthenticationWiring(platform, identity, configuration.signIn)

    private val sessions = SessionsWiring(platform, authentication.signIns, configuration.signIn)

    /**
     * Tells every module's subscribers that something happened. Nothing publishes yet, because the only
     * event, an account being deleted, has no way to happen yet; the deletion that adds one takes this
     * from here (ADR-090).
     */
    public val events: EventPublisher by lazy { EventBus(sessions.subscribers) }

    /**
     * Everything mounted, in the shape `platform:http` guarantees.
     *
     * The translator chain carries only what capability modules contribute — currently nothing.
     * `Api` supplies both ends itself, so neither can be forgotten here.
     *
     * Every route is closed to anyone who is not signed in unless it says otherwise (ADR-088), and who
     * is signed in is read from the session cookie by `sessions`.
     */
    public val api: Api by lazy {
        Api(
            routes = listOf(
                HealthRoutes(
                    reporter = HealthReporter.OverChecks(checks),
                    token = ServiceToken(configuration.healthToken.revealed()),
                ),
            ) + authentication.routes + sessions.routes + identity.routes,
            failures = FailureTranslator.Chained(emptyList()),
            trace = TraceHeader(platform.ids),
            ledger = platform.persistence.ledger,
            callers = sessions.callers,
            appOrigin = configuration.signIn.appOrigin,
        )
    }

    /**
     * Both decorators, in this order, from one place: wrapping by hand at each call site is how a
     * check ends up with only one of them.
     */
    private fun guarded(check: HealthCheck): HealthCheck =
        HealthCheck.Contained(HealthCheck.Bounded(check, BOUND, platform.abandoned))

    private companion object {
        /**
         * The same for every check. Per-check bounds are open — see `backend/.plans/`.
         */
        val BOUND = 2.seconds
    }
}
