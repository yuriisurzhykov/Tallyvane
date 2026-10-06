package tallyvane.journal.web

import tallyvane.journal.application.ShowActivityUseCase
import tallyvane.platform.http.RouteModule

/**
 * Hands out the routes this module serves, for the composition root to mount. The route classes are `internal`;
 * one takes one use case (`web-one-usecase`).
 */
public class JournalRoutesFactory {
    /**
     * `GET /security-activity`: the signed-in person's journal.
     */
    public fun activity(show: ShowActivityUseCase): RouteModule = ActivityRoutes(show, EntryWords(), ActivityProblems())

    override fun toString(): String = "JournalRoutesFactory"
}
