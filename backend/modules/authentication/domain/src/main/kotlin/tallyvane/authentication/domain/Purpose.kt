package tallyvane.authentication.domain

/**
 * Why a person is authenticating (ADR-078). Each purpose has its own policy, so registration and
 * signing in are told apart by data rather than by code paths.
 *
 * A purpose also carries the floor no policy for it may go below, so a mistaken edit, or a captured
 * admin API, cannot weaken sign-in below what ADR-078 promises.
 *
 * @param floor The least a policy for this purpose must demand.
 */
public enum class Purpose(private val floor: Floor) {
    Registration(Floor.None),
    Login(Floor.EnrolledSecondFactor),
    AdminLogin(Floor.MandatorySecondFactor),
    StepUp(Floor.EnrolledSecondFactor),
    ;

    /**
     * Whether a policy made of [steps] keeps the floor this purpose sets.
     */
    internal fun isKeptBy(steps: List<Step>): Boolean = when (floor) {
        Floor.None -> true
        Floor.EnrolledSecondFactor -> steps.any { it.demandsSecondFactorFromTheEnrolled() }
        Floor.MandatorySecondFactor -> steps.any { it.demandsSecondFactorFromEveryone() }
    }

    /**
     * The least a policy must demand.
     */
    public enum class Floor {
        /**
         * Nothing beyond identifying the account: at registration there is no factor to ask for.
         */
        None,

        /**
         * A second factor from every account that set one up. A user can only raise their own bar
         * (ADR-078): a policy that let someone with TOTP sign in, or confirm a dangerous action,
         * with Google alone would lower it for them.
         */
        EnrolledSecondFactor,

        /**
         * A second factor from every account, set up or not; those without one are let in only to
         * set it up.
         */
        MandatorySecondFactor,
    }
}
