package tallyvane.authentication.web

import tallyvane.authentication.application.BeginSignInUseCase
import tallyvane.authentication.application.BeginStepUpUseCase
import tallyvane.authentication.application.ContinueWithGoogleUseCase
import tallyvane.authentication.application.RegisterUseCase
import tallyvane.authentication.application.ShowRegistrationUseCase
import tallyvane.platform.http.RouteModule

/**
 * Hands out the routes this module serves, for the composition root to mount. The route classes are
 * `internal`; one takes one use case (`web-one-usecase`), so this is where the root, which holds all
 * of them, asks for each.
 */
public class AuthenticationRoutesFactory {
    /**
     * `POST /google-sign-in`: starting a sign-in.
     */
    public fun signIn(begin: BeginSignInUseCase): RouteModule = SignInRoutes(begin, AttemptCookie())

    /**
     * `POST /google-step-up`: starting a confirmation of a dangerous act.
     */
    public fun stepUp(begin: BeginStepUpUseCase): RouteModule = StepUpRoutes(begin, AttemptCookie())

    /**
     * `GET /google-return`: where Google sends the browser back to, which sends it on to the
     * pages of the application at [appOrigin], such as `https://app.tallyvane.com`.
     */
    public fun googleReturn(continueWith: ContinueWithGoogleUseCase, appOrigin: String): RouteModule =
        GoogleReturnRoutes(continueWith, AttemptCookie(), ReturnPages(appOrigin))

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
