package tallyvane.identity.application.session

import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.domain.token.RefreshTokenRetentionPolicy
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * Deletes every refresh token row past [RefreshTokenRetentionPolicy]'s own absolute cap — the
 * housekeeping side of RFC 9700 §4.14.2, distinct from [RefreshSessionUseCase]'s own enforcement:
 * a token this deletes was already unable to redeem past that same cap, so this exists to keep
 * `identity.refresh_tokens` from growing without bound, not to make the cap take effect.
 *
 * `internal`, not a [tallyvane.platform.kernel.UseCase]: nobody signs in to trigger this, a
 * scheduler would — and building that scheduler is explicitly out of scope for this pass, per
 * `backend/.plans/identity-implementation.md`. This class is what a future one calls.
 */
internal interface PruneExpiredRefreshTokens {
    /**
     * @return How many rows were deleted, so a future scheduled caller has something to log.
     */
    suspend fun prune(): Int

    /**
     * One [transactions.inTransaction] covers the whole method, per the same rule stated
     * throughout this package and checked for real in `backend/playground/transactions/README.md`.
     */
    class Default(
        private val refreshTokens: RefreshTokenStore,
        private val retention: RefreshTokenRetentionPolicy,
        private val clock: Clock,
        private val transactions: TransactionRunner,
    ) : PruneExpiredRefreshTokens {
        override suspend fun prune(): Int = transactions.inTransaction {
            Verdict.Commit(refreshTokens.deleteIssuedBefore(retention.cutoff(clock.now())))
        }
    }
}
