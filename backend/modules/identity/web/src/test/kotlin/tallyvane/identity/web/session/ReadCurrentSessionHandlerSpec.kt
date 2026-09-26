package tallyvane.identity.web.session

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlin.uuid.Uuid
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.web.routing.AuthRoutes
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.ResolvedIdentity
import tallyvane.platform.http.Api
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.kernel.IdGenerator

class ReadCurrentSessionHandlerSpec : StringSpec({
    "returns no content when the access session is valid" {
        testApplication {
            application { sessionApi(currentPrincipal(identity())) }

            val response = client.get("/api/v1/auth/session")
            response.status shouldBe HttpStatusCode.NoContent
            response.headers["Cache-Control"] shouldBe "no-store"
        }
    }

    "returns unauthorized when there is no current session" {
        testApplication {
            application { sessionApi(currentPrincipal(null)) }

            client.get("/api/v1/auth/session").status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

private fun Application.sessionApi(current: CurrentPrincipal) {
    Api(
        listOf(AuthRoutes.Installation(listOf(ReadCurrentSessionHandler(current)), false)),
        FailureTranslator.Chained(emptyList()),
        TraceHeader(IdGenerator.Uuid7()),
    ).install(this)
}

private fun currentPrincipal(identity: ResolvedIdentity?): CurrentPrincipal = object : CurrentPrincipal {
    override suspend fun resolve(call: ApplicationCall): ResolvedIdentity? = identity
}

private fun identity() = ResolvedIdentity(
    UserId(Uuid.parse("00000000-0000-7000-8000-000000000121")),
    SessionId(Uuid.parse("00000000-0000-7000-8000-000000000122")),
)
