package tallyvane.identity.contract

/**
 * The one thing any other module will ever know about `identity` — this interface, and nothing
 * else. No module reads `identity`'s tables, sees a `Session` row, or imports anything from
 * `identity:domain` or `identity:infrastructure`; every one of those is invisible outside this
 * module by the ordinary rule that a capability publishes only its `contract`.
 *
 * This closes option A of `backend/.plans/backend-access-and-api.md`'s "how does each module know
 * the user" question: a session cookie is turned into a [ResolvedPrincipal] exactly once, at the
 * HTTP boundary, by whatever real implementation `identity:infrastructure` supplies. Every use case
 * downstream — in `jobs`, `applications`, anywhere — receives the result as an ordinary parameter,
 * never as a second query against this interface for the same request.
 *
 * `platform:http` cannot depend on this contract directly (`platform` may never depend on
 * `modules:*`), so it exposes a generic extension point instead — "run this before every route,
 * store the result on the call" — and the composition root, `server`, is the one place that both
 * `platform:http` and this contract are visible at once, wiring the real implementation into that
 * extension point. That wiring is not this pass's slice; it arrives with the real implementation
 * over `SessionStore`, once one exists.
 */
public interface PrincipalResolver {
    /**
     * Turns a raw session cookie into who is making the request, or `null` if the cookie does not
     * currently name a valid session — never issued, expired, or revoked. Those three are
     * deliberately not distinguished in the return type: every one of them means "there is no
     * request-scoped identity to hand downstream", and a caller outside `identity` has no
     * different action to take for any of the three.
     *
     * @param rawSessionCookie the opaque value read from the session cookie, exactly as the
     * browser sent it — not a token by itself. Turning this string into a lookup is entirely
     * `identity:infrastructure`'s business; nothing about its shape is part of this contract.
     */
    public suspend fun resolve(rawSessionCookie: String): ResolvedPrincipal?
}
