package tallyvane.identity.contract

/**
 * Who may administer the system, as `identity` answers it to its neighbours (ADR-097).
 *
 * An administrator is an account that was given the right, by an operator and not through the API. The
 * neighbours that open a session on the administrators' site, and the ones that change what the system
 * demands of a sign-in, ask here; nothing else about the right is published.
 *
 * Runs inside the caller's transaction and never opens one (`TransactionRunner`, ADR-052).
 */
public interface Admins {
    /**
     * Whether [account] holds the right to administer. Asked afresh every time, so taking the right away
     * works from the next request and not from the end of a session.
     */
    public fun isAdmin(account: AccountId): Boolean

    /**
     * Every account that holds the right now, in no particular order. A short list: an operator gives the
     * right by hand (ADR-097). Asked by whoever must know whether a change would leave nobody able to sign in
     * as one (ADR-098).
     */
    public fun all(): List<AccountId>
}
