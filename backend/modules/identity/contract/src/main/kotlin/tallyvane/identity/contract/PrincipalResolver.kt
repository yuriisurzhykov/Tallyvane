package tallyvane.identity.contract

/**
 * The one thing any other module will ever know about `identity` — this interface, and nothing
 * else. Every use case downstream — in `jobs`, `applications`, anywhere — receives the result as
 * an ordinary parameter, resolved once per request, never as a second query against this
 * interface for the same request.
 *
 * Why this is the chosen shape, and how `server` will eventually wire the real implementation into
 * `platform:http`'s request pipeline: `contract/README.md`.
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
