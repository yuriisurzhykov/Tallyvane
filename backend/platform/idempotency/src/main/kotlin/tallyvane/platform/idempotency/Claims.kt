package tallyvane.platform.idempotency

/**
 * Takes a claim inside the transaction that is already open, which is the whole of its contract.
 *
 * Only [ClaimedTransactions] calls it, as the first thing a claimed transaction does. It is a separate
 * port from [Ledger] because the two are asked at different times by different callers: the edge asks
 * the ledger around the transaction, on connections of its own, and this is asked inside it, on the
 * transaction's connection, so that the claim shares the fate of the work.
 */
public interface Claims {
    /**
     * Records [claim] as taken, in the open transaction.
     *
     * @throws ClaimedElsewhere when another committed transaction already holds it. The caller's
     * transaction is rolled back by the exception passing through, so nothing else it wrote stays.
     * @throws ClaimBusy when another transaction that holds it has not finished and did not finish
     * within the database's lock wait.
     */
    public fun take(claim: Claim)
}
