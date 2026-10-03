package tallyvane.platform.http

/**
 * Who a [RouteModule] lets in.
 */
public enum class Access {
    /**
     * Only a signed-in person. What a module gets unless it says otherwise, so a route added without
     * a thought about access is closed, not open.
     */
    Signed,

    /**
     * Only a signed-in person who proved who they are recently enough: the dangerous acts, such as ending
     * the sessions on other devices. A person whose proof is older is told to confirm it again, with
     * `403` `step-up-required`, and goes on when they have (ADR-092).
     *
     * The edge decides, not the route, so a route cannot forget to ask and cannot ask differently.
     */
    SignedFresh,

    /**
     * Anyone, signed in or not: the sign-in steps themselves, and the health probe, which is
     * guarded by its own token.
     */
    Public,
}
