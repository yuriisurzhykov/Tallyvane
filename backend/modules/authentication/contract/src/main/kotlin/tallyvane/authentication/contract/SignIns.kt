package tallyvane.authentication.contract

import tallyvane.platform.kernel.Secret

/**
 * What `authentication` offers a module that grants access: to take a completed sign-in and say
 * whose it was (ADR-076).
 *
 * `authentication` decides whether the person proved enough, against the policy in force, and cannot
 * grant access itself; `sessions` grants it, and only from what this hands over. A sign-in is single
 * use: redeeming it forgets it, so the same secret never redeems twice.
 *
 * Runs inside the caller's transaction and never opens one (`TransactionRunner`, ADR-052), so
 * "forget the sign-in" and "issue the session" commit or roll back together.
 */
public interface SignIns {
    /**
     * Takes the completed sign-in the browser's [secret] belongs to.
     *
     * Reports nothing to redeem when there is none under the secret, when it is not complete under its
     * policy (still waiting for a factor, ended, expired), or when its person has no account yet.
     * Nothing is forgotten in those cases, so a registration still waiting for its welcome form is
     * not lost to a request that came too early.
     */
    public fun redeem(secret: Secret): Redemption

    /**
     * Takes the completed confirmation the browser's [secret] belongs to: a person who is already signed in
     * proved who they are again (ADR-092).
     *
     * A confirmation is single use like a sign-in, and a different thing: it never grants a session, only
     * says whose proof it was and when it was given, and the one that takes it decides whether that is the
     * person it was for. A sign-in cannot be taken as a confirmation, nor the other way round, so neither
     * can be used for the other's purpose. Reports nothing to redeem in every case [redeem] does.
     */
    public fun redeemStepUp(secret: Secret): Redemption
}
