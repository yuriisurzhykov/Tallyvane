package tallyvane.identity.application.port

import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenFamilyState
import kotlin.time.Instant

/**
 * A [RefreshTokenStore] backed by an in-memory map, for a use case's test to inspect what was
 * saved without a real database.
 */
internal class RefreshTokenStoreFake : RefreshTokenStore {
    private data class Row(
        val sessionId: SessionId,
        val familyId: TokenFamilyId,
        val hash: HashedToken,
        var consumed: Boolean,
        var revoked: Boolean,
        val issuedAt: Instant,
    )

    private val rows: MutableList<Row> = mutableListOf()

    override suspend fun issueFirst(
        sessionId: SessionId,
        familyId: TokenFamilyId,
        hash: HashedToken,
        expiresAt: Instant,
        issuedAt: Instant,
    ) {
        rows += Row(sessionId, familyId, hash, consumed = false, revoked = false, issuedAt)
    }

    override suspend fun stateOf(hash: HashedToken): TokenFamilyState? {
        val row = rows.find { it.hash == hash } ?: return null
        return TokenFamilyState(row.sessionId, used = row.consumed || row.revoked)
    }

    override suspend fun rotate(
        oldHash: HashedToken,
        newHash: HashedToken,
        expiresAt: Instant,
        now: Instant,
    ): RefreshTokenStore.RotateOutcome {
        val row = rows.find { it.hash == oldHash }
        return if (row == null || row.consumed || row.revoked) {
            RefreshTokenStore.RotateOutcome.AlreadyRotated
        } else {
            row.consumed = true
            rows += Row(row.sessionId, row.familyId, newHash, consumed = false, revoked = false, now)
            RefreshTokenStore.RotateOutcome.Rotated(row.sessionId)
        }
    }

    override suspend fun revokeAllFor(sessionId: SessionId) {
        rows.filter { it.sessionId == sessionId && !it.consumed }.forEach { it.revoked = true }
    }

    override suspend fun deleteIssuedBefore(cutoff: Instant): Int {
        val before = rows.size
        rows.removeAll { it.issuedAt < cutoff }
        return before - rows.size
    }
}
