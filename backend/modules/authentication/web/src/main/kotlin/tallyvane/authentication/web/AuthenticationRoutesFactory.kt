package tallyvane.authentication.web

import tallyvane.authentication.application.BeginSignInUseCase
import tallyvane.authentication.application.BeginStepUpUseCase
import tallyvane.authentication.application.ContinueWithGoogleUseCase
import tallyvane.authentication.application.RegisterUseCase
import tallyvane.authentication.application.ShowRegistrationUseCase
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.Surfaces

/**
 * Hands out the routes this module serves, for the composition root to mount. The route classes are
 * `internal`; one takes one use case (`web-one-usecase`), so this is where the root, which holds all
 * of them, asks for each.
 */
public class AuthenticationRoutesFactory {
    /**
     * `POST /google-sign-in`: starting a sign-in, for the door [surfaces] says the request came through.
     */
    public fun signIn(begin: BeginSignInUseCase, surfaces: Surfaces): RouteModule =
        SignInRoutes(begin, AttemptCookie(), surfaces)

    /**
     * `POST /google-step-up`: starting a confirmation of a dangerous act.
     */
    public fun stepUp(begin: BeginStepUpUseCase, surfaces: Surfaces): RouteModule =
        StepUpRoutes(begin, AttemptCookie(), surfaces)

    /**
     * `GET /google-return`: where Google sends the browser back to, which sends it on to the
     * pages of the application at the origin of the door the request came through, as [surfaces] knows
     * them.
     */
    public fun googleReturn(continueWith: ContinueWithGoogleUseCase, surfaces: Surfaces): RouteModule =
        GoogleReturnRoutes(continueWith, AttemptCookie(), ReturnPages(surfaces), surfaces)

    /**
     * `GET /welcome`: what the welcome form starts from.
     */
    public fun welcome(show: ShowRegistrationUseCase): RouteModule =
        WelcomeRoutes(show, AttemptCookie(), WelcomeProblems())

    /**
     * `POST /registration`: finishing the welcome form.
     */
    public fun registration(register: RegisterUseCase): RouteModule =
        RegistrationRoutes(register, AttemptCookie(), RegistrationProblems())

    override fun toString(): String = "AuthenticationRoutesFactory"
}
