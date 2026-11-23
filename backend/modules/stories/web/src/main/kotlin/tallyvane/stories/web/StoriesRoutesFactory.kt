package tallyvane.stories.web

import tallyvane.platform.http.RouteModule
import tallyvane.stories.application.CorrectFactUseCase
import tallyvane.stories.application.ShowStoriesUseCase
import tallyvane.stories.application.TellUseCase

public class StoriesRoutesFactory {
    public fun tell(tell: TellUseCase): RouteModule = TellRoutes(tell, StoryProblems())

    public fun correction(correct: CorrectFactUseCase): RouteModule = CorrectionRoutes(correct, StoryProblems())

    public fun shelf(show: ShowStoriesUseCase): RouteModule = ShelfRoutes(show)

    override fun toString(): String = "StoriesRoutesFactory"
}
