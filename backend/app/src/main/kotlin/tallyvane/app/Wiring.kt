package tallyvane.app

import tallyvane.app.config.Configuration
import tallyvane.platform.http.Api
import tallyvane.platform.observability.health.HealthCheck

/**
 * The root, and it should read as a table of contents.
 *
 * Skeleton: bodies are `TODO()` until `WiringSpec` is seen failing.
 *
 * One line per capability, not one line per object: a capability module contributes its own
 * `<Feature>Wiring`, which takes what it needs in its constructor and publishes what it offers.
 * ADR-010 names the measurable test of whether this is still working — can a module be added
 * without reading all of this — and what to do when the answer becomes no.
 */
public class Wiring(private val platform: PlatformWiring, private val configuration: Configuration) {
    /**
     * Every check the aggregate reports on, each already wrapped in the two decorators ADR-054
     * requires, so no check can be slow or throwing by the time the reporter sees it.
     */
    public val checks: List<HealthCheck> get() = TODO()

    /**
     * Everything mounted, in the shape `platform:http` guarantees.
     */
    public val api: Api get() = TODO()
}
