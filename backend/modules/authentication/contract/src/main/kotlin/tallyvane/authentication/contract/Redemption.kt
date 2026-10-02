package tallyvane.authentication.contract

import tallyvane.identity.contract.AccountId
import kotlin.time.Instant

/**
 * What came of asking for a completed sign-in.
 *
 * Their contents are private, and the only way to read them is [reportTo], so a caller must say what it
 * does in each case.
 */
public sealed interface Redemption {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * What a [Redemption] says about itself, one method per case.
     */
    public interface Report<out T> {
        /**
         * The sign-in was complete and is now forgotten.
         *
         * @param account Whose it was.
         * @param proofs How they proved it, which the session keeps.
         * @param authenticatedAt When the last of it was proved; freshness for dangerous actions counts
         * from here.
         */
        public fun redeemed(account: AccountId, proofs: Set<Proof>, authenticatedAt: Instant): T

        public fun nothingToRedeem(): T
    }
}
