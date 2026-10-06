package tallyvane.server

import tallyvane.journal.application.Journal
import tallyvane.journal.application.ShowActivityUseCase
import tallyvane.journal.contract.SecurityJournal
import tallyvane.journal.infrastructure.JournalStorageFactory
import tallyvane.journal.web.JournalRoutesFactory
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Clock

/**
 * `journal`, as its neighbours see it: the contract the others tell what happened through, and the route a
 * signed-in person reads their own entries on (ADR-095).
 *
 * Built first, because `authentication` and `sessions` are handed its contract. Nothing here touches a
 * database; the adapters run inside the transaction of whoever tells or asks.
 */
public class JournalWiring(private val platform: PlatformWiring) {
    private val storage = JournalStorageFactory()

    /**
     * What the modules that cause an entry tell.
     */
    public val contract: SecurityJournal by lazy { Journal(storage.entries(), storage.notifier(), Clock.Wall()) }

    private val show: ShowActivityUseCase by lazy {
        ShowActivityUseCase.ShowActivity(storage.entries(), platform.persistence.transactions)
    }

    /**
     * The routes this module serves.
     */
    public val routes: List<RouteModule> by lazy { listOf(JournalRoutesFactory().activity(show)) }

    override fun toString(): String = "JournalWiring"
}
