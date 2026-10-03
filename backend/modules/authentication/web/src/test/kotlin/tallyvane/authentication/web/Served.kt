package tallyvane.authentication.web

import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleProfile
import tallyvane.authentication.application.Harness
import tallyvane.platform.http.Api
import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.idempotency.LedgerFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant

const val APP = "https://app.example.test"

/**
 * The module's four routes over the real use cases and fakes of every port, behind the real edge.
 */
class Served(val harness: Harness = Harness()) {
    private val routes = AuthenticationRoutesFactory()

    fun api(): Api = Api(
        routes = modules(),
        failures = FailureTranslator.Chained(emptyList()),
        trace = TraceHeader(IdGeneratorFake()),
        ledger = LedgerFake(TransactionRunnerFake(), ClockFake(Instant.parse("2026-10-02T09:00:00Z"))),
        callers = Callers.Anonymous(),
        appOrigin = APP,
    )

    /**
     * A person who pressed "Sign in" and came back from Google as somebody with no account: the cookie
     * their browser holds for the welcome form.
     */
    suspend fun registering(): Secret {
        val pressed = harness.pressSignIn()
        val answer = GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com"))
        return Secret(harness.line(harness.returnWith(pressed, answer)).substringAfter("registering "))
    }

    private fun modules(): List<RouteModule> = listOf(
        routes.signIn(harness.begin),
        routes.stepUp(harness.beginStepUp),
        routes.googleReturn(harness.continueWith, APP),
        routes.welcome(harness.show),
        routes.registration(harness.register),
    )

    override fun toString(): String = "Served"
}
