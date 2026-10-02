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
     * Anyone, signed in or not: the sign-in steps themselves, and the health probe, which is
     * guarded by its own token.
     */
    Public,
}
