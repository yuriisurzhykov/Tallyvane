package tallyvane.platform.idempotency

/**
 * What the system remembers about requests it has already seen, asked by the edge around the
 * transaction rather than inside it.
 *
 * Every method runs on a connection of its own. That is the point of splitting this from [Claims]:
 * [earlier] must see only committed claims, and [record] must happen after the work committed, so
 * neither can share the work's transaction.
 */
public interface Ledger {
    /**
     * What is known about [claim] now. Sees only committed claims, and ignores expired ones.
     */
    public suspend fun earlier(claim: Claim): Earlier

    /**
     * Keeps [answer] as the answer to [claim], if the claim was committed and nothing was kept yet.
     *
     * Does nothing when there is no committed claim, which is what a request whose work rolled back
     * leaves: there is nothing to answer a repeat with, and the repeat should run.
     */
    public suspend fun record(claim: Claim, answer: Answer)

    /**
     * Marks the answer to [claim] as one that was not kept on purpose, so a repeat is told so at
     * once instead of waiting for an answer that will not come.
     */
    public suspend fun withhold(claim: Claim)

    /**
     * Deletes every claim whose time is up.
     *
     * @return how many.
     */
    public suspend fun forgetExpired(): Int
}
