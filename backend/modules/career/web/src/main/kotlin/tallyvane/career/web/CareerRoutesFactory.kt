package tallyvane.career.web

import tallyvane.career.application.SetGoalUseCase
import tallyvane.career.application.ShowHomeUseCase
import tallyvane.career.application.ShowResumeUseCase
import tallyvane.platform.http.RouteModule

public class CareerRoutesFactory {
    public fun home(show: ShowHomeUseCase): RouteModule = HomeRoutes(show)

    public fun goal(set: SetGoalUseCase): RouteModule = GoalRoutes(set, GoalProblems())

    public fun resume(show: ShowResumeUseCase): RouteModule = ResumeRoutes(show)

    override fun toString(): String = "CareerRoutesFactory"
}
