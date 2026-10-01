package tallyvane.authentication.domain

/**
 * Why a person is authenticating (ADR-078). Each purpose has its own policy, so registration and
 * signing in are told apart by data rather than by code paths.
 *
 * A purpose also carries the floor no policy for it may go below. Only one exists so far: an
 * administrator always shows a second factor, so a mistaken edit, or a captured admin API, cannot
 * make `admin_login` a Google-only sign-in.
 *
 * @param secondFactorIsMandatory Whether every policy for this purpose must demand, from every
 * account, a factor that has to be set up first.
 */
public enum class Purpose(private val secondFactorIsMandatory: Boolean) {
    Registration(secondFactorIsMandatory = false),
    Login(secondFactorIsMandatory = false),
    AdminLogin(secondFactorIsMandatory = true),
    StepUp(secondFactorIsMandatory = false),
    ;

    /**
     * Whether a policy made of [steps] keeps the floor this purpose sets.
     */
    internal fun isKeptBy(steps: List<Step>): Boolean =
        !secondFactorIsMandatory || steps.any { it.demandsSecondFactorFromEveryone() }
}
