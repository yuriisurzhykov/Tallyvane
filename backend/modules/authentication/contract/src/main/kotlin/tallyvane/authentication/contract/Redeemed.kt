package tallyvane.authentication.contract

import tallyvane.identity.contract.AccountId
import kotlin.time.Instant

/**
 * A completed sign-in, taken.
 */
public class Redeemed(
    private val account: AccountId,
    private val proofs: Set<Proof>,
    private val authenticatedAt: Instant,
) : Redemption {
    override fun <T> reportTo(report: Redemption.Report<T>): T = report.redeemed(account, proofs, authenticatedAt)

    override fun toString(): String = "Redeemed"
}
