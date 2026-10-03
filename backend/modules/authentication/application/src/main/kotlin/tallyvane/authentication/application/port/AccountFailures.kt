package tallyvane.authentication.application.port

import tallyvane.authentication.domain.AccountGuesses
import tallyvane.identity.contract.AccountId
import kotlin.time.Instant

/**
 * Where the wrong TOTP codes typed for each account are kept, whichever attempt they came in (ADR-082).
 *
 * Runs inside the caller's transaction. A caller that records a wrong code and then refuses the request
 * must commit the transaction anyway: a failure recorded in a transaction that rolls back is forgotten
 * with the refusal, which is how a limit becomes something a client can beat.
 */
public interface AccountFailures {
    /**
     * The wrong codes typed for [account] at or after [since].
     */
    public fun recent(account: AccountId, since: Instant): AccountGuesses

    /**
     * Records a wrong code typed for [account] at [at]. Failures older than [AccountGuesses.WINDOW] before
     * [at] are of no use to anyone and are removed.
     */
    public fun record(account: AccountId, at: Instant)
}
