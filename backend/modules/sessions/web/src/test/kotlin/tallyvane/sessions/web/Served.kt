package tallyvane.sessions.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import tallyvane.platform.http.APP_ORIGIN
import tallyvane.platform.http.Api
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.sessions.application.Harness
import kotlin.time.Instant

/**
 * The module's routes over the real use cases and fakes of every port, behind the real edge, with a
 * `/probe` route that only a signed-in person reaches.
 */
class Served(val harness: Harness = Harness()) {
    private val routes = SessionsWebFactory()

    fun api(extra: List<RouteModule> = emptyList()): Api = Api(
        routes = listOf(
            routes.open(harness.open),
            routes.stepUp(harness.confirmStepUp),
            routes.signOut(harness.signOut),
            routes.devices(harness.listDevices),
            routes.deviceSignOut(harness.revokeDevice),
            routes.deviceName(harness.renameDevice),
            routes.otherDevicesSignOut(harness.signOutOthers),
        ) + extra,
        failures = FailureTranslator.Chained(emptyList()),
        trace = TraceHeader(IdGeneratorFake()),
        ledger = LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-02T09:00:00Z"))),
        callers = routes.callers(harness.authenticate),
        appOrigin = APP_ORIGIN,
    )

    override fun toString(): String = "Served"
}

fun HttpRequestBuilder.withCookie(name: String, secret: Secret) {
    header(HttpHeaders.Cookie, "$name=${secret.revealed()}")
}
