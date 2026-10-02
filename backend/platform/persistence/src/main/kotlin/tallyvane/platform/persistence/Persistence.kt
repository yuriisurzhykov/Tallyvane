package tallyvane.platform.persistence

import tallyvane.platform.idempotency.Ledger
import tallyvane.platform.kernel.TransactionRunner

/**
 * What this module offers the rest of the system, without saying what it runs on.
 *
 * Consumers depend on this and never on `PostgresPersistence`: Hikari, Exposed and a
 * JDBC url are that implementation's business, and naming it at a call site would nail
 * the choice into every caller. Only the composition root names an implementation.
 *
 * Deliberately not `AutoCloseable`. Closing belongs to whoever created the thing, and
 * that is the composition root alone; a consumer holding this can reach `transactions`
 * and nothing else, so it cannot shut down a pool other code is using. The
 * implementation is what carries the lifetime.
 */
public interface Persistence {
    /**
     * Transaction boundaries over this module's database.
     *
     * When the request being served carries a claim (an `Idempotency-Key`), the transaction takes it
     * as its first statement, so the claim commits with the work or is gone with it (ADR-086). The
     * runner an undecorated pool would offer is not published at all: a use case that could obtain one
     * could write without being claimed.
     */
    public val transactions: TransactionRunner

    /**
     * What is remembered about requests already seen, asked around a transaction and never inside one.
     */
    public val ledger: Ledger
}
