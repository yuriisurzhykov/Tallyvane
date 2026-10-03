package tallyvane.authentication.web

import tallyvane.authentication.application.BeginTotpUseCase
import tallyvane.authentication.application.ConfirmTotpUseCase
import tallyvane.authentication.application.DisableTotpUseCase
import tallyvane.authentication.application.RegenerateRecoveryCodesUseCase
import tallyvane.authentication.application.ShowSecondFactorUseCase
import tallyvane.authentication.application.ShowSignInUseCase
import tallyvane.authentication.application.VerifySecondFactorUseCase
import tallyvane.platform.http.RouteModule

/**
 * Hands out the routes of the second step of a sign-in and of managing TOTP (ADR-093), for the
 * composition root to mount. The route classes are `internal`; one takes one use case.
 */
public class SecondFactorRoutesFactory {
    /**
     * `GET /sign-in`: where a sign-in or a confirmation stands.
     */
    public fun signInState(show: ShowSignInUseCase): RouteModule =
        SignInStateRoutes(show, AttemptCookie(), SignInStateProblems())

    /**
     * `POST /second-factor-codes`: answering a second step.
     */
    public fun secondFactorCodes(verify: VerifySecondFactorUseCase): RouteModule =
        SecondFactorCodeRoutes(verify, AttemptCookie(), SecondFactorCodeProblems())

    /**
     * `GET /second-factor`: what the signed-in person has set up.
     */
    public fun secondFactor(show: ShowSecondFactorUseCase): RouteModule = SecondFactorRoutes(show)

    /**
     * `POST /totp-enrollments`: beginning to turn TOTP on.
     */
    public fun totpEnrollments(begin: BeginTotpUseCase): RouteModule = TotpEnrollmentRoutes(begin, TotpBeginProblems())

    /**
     * `POST /totp-confirmations`: typing the first code.
     */
    public fun totpConfirmations(confirm: ConfirmTotpUseCase): RouteModule =
        TotpConfirmationRoutes(confirm, TotpConfirmationProblems())

    /**
     * `DELETE /totp-enrollment`: turning TOTP off.
     */
    public fun totpEnrollment(disable: DisableTotpUseCase): RouteModule =
        TotpRemovalRoutes(disable, TotpRemovalProblems())

    /**
     * `POST /recovery-codes`: asking for ten new recovery codes.
     */
    public fun recoveryCodes(regenerate: RegenerateRecoveryCodesUseCase): RouteModule =
        RecoveryCodeRoutes(regenerate, RecoveryCodeProblems())

    override fun toString(): String = "SecondFactorRoutesFactory"
}
