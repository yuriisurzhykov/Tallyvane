package tallyvane.identity.contract

/**
 * How [Accounts.register] ended.
 *
 * Closed, and read only through [reportTo], so a caller that forgets a case does not compile.
 */
public sealed interface Registration {
    public fun <T> reportTo(report: Report<T>): T

    /**
     * A reader of [Registration], one method per case.
     */
    public interface Report<out T> {
        /**
         * The account exists, made now or by an earlier registration of the same person.
         */
        public fun registered(account: AccountId): T

        /**
         * The chosen name is not one `identity` accepts: empty after trimming, too long, or holding
         * characters that are not text. Nothing was created.
         */
        public fun nameRefused(): T
    }
}
