package tallyvane.platform.http

/**
 * Resolves "who is making this request" from the raw session cookie — a generic extension point
 * rather than a call to `identity` directly, because `platform:http` may never depend on
 * `modules:*` (§4.4) and therefore cannot name a `Principal` type at all. The composition root is
 * the only place both sides are visible, and it wires `identity`'s own `PrincipalResolver` in
 * behind this shape.
 */
public interface RequestPrincipalResolver {
    /**
     * @param rawSessionCookie The cookie's raw value exactly as the browser sent it, or `null` if
     * the request carried none.
     * @return Whatever the real resolver considers this request's principal, or `null` if the
     * cookie named no currently valid session — including "there was no cookie at all", which
     * this interface does not distinguish from "the cookie was invalid": a caller downstream has
     * no different action for either.
     */
    public suspend fun resolve(rawSessionCookie: String?): Any?
}
