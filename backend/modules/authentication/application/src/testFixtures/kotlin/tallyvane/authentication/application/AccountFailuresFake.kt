package tallyvane.authentication.application

import tallyvane.authentication.application.port.AccountFailures
import tallyvane.authentication.domain.AccountGuesses
import tallyvane.identity.contract.AccountId
import kotlin.time.Instant

/**
 * The wrong codes of each account, in a map, for tests of the code that uses the port (ADR-044).
 */
class AccountFailuresFake : AccountFailures {
    private val failures = mutableMapOf<AccountId, List<Instant>>()

    override fun recent(account: AccountId, since: Instant): AccountGuesses =
        AccountGuesses.at(failures[account].orEmpty().filter { it >= since })

    override fun record(account: AccountId, at: Instant) {
        failures[account] = failures[account].orEmpty().filter { it >= at - AccountGuesses.WINDOW } + at
    }

    override fun toString(): String = "AccountFailuresFake(${failures.size})"
}
