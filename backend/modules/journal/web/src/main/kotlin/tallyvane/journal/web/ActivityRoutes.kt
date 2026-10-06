package tallyvane.journal.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.contract.AccountId
import tallyvane.journal.application.ActivityOutcome
import tallyvane.journal.application.ShowActivityUseCase
import tallyvane.journal.domain.EntryKind
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The security journal of the signed-in person (ADR-083).
 *
 * ```
 * GET /api/v1/security-activity?before=<cursor>   their entries, the newest first, a page at a time
 * ```
 *
 * Closed to everyone but a signed-in person, as every route is unless it says otherwise, and it shows nobody's
 * entries but theirs: the account is the one the edge found, never one the request names. Never cached: the next
 * person on the same browser must not be shown this one's.
 */
internal class ActivityRoutes(
    private val show: ShowActivityUseCase,
    private val words: EntryWords,
    private val problems: ActivityProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/security-activity")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (
                val outcome = show.show(
                    AccountId(Requester(call).account()),
                    call.request.queryParameters["before"],
                )
            ) {
                is ActivityOutcome.Shown -> call.respond(shownBy(outcome))
                is ActivityOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun shownBy(outcome: ActivityOutcome.Shown): ActivityShown {
        val page = Page(words)
        outcome.writeTo(page)
        return page.shown()
    }

    override fun toString(): String = "ActivityRoutes"

    /**
     * Collects a page as the body the route answers with.
     */
    private class Page(private val words: EntryWords) : ActivityOutcome.Shown.Record {
        private val entries = mutableListOf<EntryShown>()
        private var next: String? = null

        fun shown(): ActivityShown = ActivityShown(entries.toList(), next)

        override fun entry(
            account: Uuid,
            kind: EntryKind,
            occurredAt: Instant,
            session: Uuid?,
            firstFromDevice: Boolean,
            codesLeft: Int?,
        ) {
            entries += EntryShown(words.of(kind), occurredAt.toString(), null, firstFromDevice, codesLeft)
        }

        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
            val last = entries.removeAt(entries.lastIndex)
            entries += EntryShown(
                last.kind,
                last.occurredAt,
                DeviceShown(browser, platform, mobile, name),
                last.firstFromDevice,
                last.codesLeft,
            )
        }

        override fun next(cursor: String?) {
            next = cursor
        }
    }
}
