package tallyvane.server

import tallyvane.career.application.SetGoalUseCase
import tallyvane.career.application.ShowHomeUseCase
import tallyvane.career.application.ShowResumeUseCase
import tallyvane.career.infrastructure.CareerStorageFactory
import tallyvane.career.web.CareerRoutesFactory
import tallyvane.catalog.infrastructure.CatalogFactory
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Clock
import tallyvane.platform.llm.ModelFactory
import tallyvane.platform.llm.ProviderRecords
import tallyvane.server.config.ModelConfiguration
import tallyvane.stories.application.CorrectFactUseCase
import tallyvane.stories.application.Listening
import tallyvane.stories.application.ShowStoriesUseCase
import tallyvane.stories.application.StoriesExperience
import tallyvane.stories.application.TellUseCase
import tallyvane.stories.infrastructure.StoriesFactory
import tallyvane.stories.web.StoriesRoutesFactory

/**
 * The product core (docs/backend/08-product-core.md): the model gateway, the catalog, stories and career. Each
 * collaborator is built once here and handed to whoever needs it.
 */
public class ProductWiring(
    private val platform: PlatformWiring,
    private val model: ModelConfiguration,
    private val records: ProviderRecords,
) {
    private val transactions get() = platform.persistence.transactions
    private val clock = Clock.Wall()
    private val catalog by lazy { CatalogFactory().catalog() }
    private val storage = StoriesFactory()
    private val stories by lazy { storage.stories() }
    private val gateway by lazy {
        // The memo, the spending and the call log commit on their own, outside the request's one transaction.
        ModelFactory().model(records, model.tokensADay.toLong(), platform.persistence.bookkeeping, clock)
    }
    private val reader by lazy { storage.reader(gateway, catalog.skills().map { it.slug }) }
    private val listening by lazy { Listening(storage.utterances(), stories, transactions, clock, platform.ids) }
    private val experience by lazy { StoriesExperience(stories, transactions) }
    private val goals by lazy { CareerStorageFactory().goals() }

    public val routes: List<RouteModule> by lazy {
        val storyRoutes = StoriesRoutesFactory()
        val careerRoutes = CareerRoutesFactory()
        listOf(
            storyRoutes.tell(TellUseCase.Tell(reader, listening, catalog, platform.ids)),
            storyRoutes.correction(
                CorrectFactUseCase.CorrectFact(reader, listening, catalog, platform.ids),
            ),
            storyRoutes.shelf(ShowStoriesUseCase.ShowStories(stories, transactions)),
            careerRoutes.home(ShowHomeUseCase.ShowHome(goals, experience, catalog, transactions)),
            careerRoutes.goal(SetGoalUseCase.SetGoal(goals, catalog, transactions)),
            careerRoutes.resume(ShowResumeUseCase.ShowResume(experience, catalog)),
        )
    }

    override fun toString(): String = "ProductWiring"
}
