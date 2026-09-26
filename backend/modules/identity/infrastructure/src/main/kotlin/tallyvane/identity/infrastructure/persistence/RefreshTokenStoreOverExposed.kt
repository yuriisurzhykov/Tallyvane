package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenFamilyState
import kotlin.time.Instant

/**
 * [RefreshTokenStore] over [RefreshTokensTable], for a real Postgres. Opens no transaction of its
 * own — see that port's own KDoc for why.
 */
internal class RefreshTokenStoreOverExposed : RefreshTokenStore {
    private val instant = InstantColumn()

    override suspend fun issueFirst(
        sessionId: SessionId,
        familyId: TokenFamilyId,
        hash: HashedToken,
        expiresAt: Instant,
        issuedAt: Instant,
    ) {
        RefreshTokensTable.insert {
            it[RefreshTokensTable.hash] = hash.hash.revealed()
            it[RefreshTokensTable.familyId] = familyId.value
            it[RefreshTokensTable.sessionId] = sessionId.value
            it[pepperVersion] = hash.pepperVersion
            it[status] = RefreshTokenStatus.ACTIVE.name.lowercase()
            it[RefreshTokensTable.issuedAt] = instant.toColumn(issuedAt)
            it[RefreshTokensTable.expiresAt] = instant.toColumn(expiresAt)
        }
    }

    override suspend fun stateOf(hash: HashedToken): TokenFamilyState? {
        val row = RefreshTokensTable
            .selectAll()
            .where { RefreshTokensTable.hash eq hash.hash.revealed() }
            .singleOrNull() ?: return null
        val status = RefreshTokenStatus.valueOf(row[RefreshTokensTable.status].uppercase())
        return TokenFamilyState(
            sessionId = SessionId(row[RefreshTokensTable.sessionId]),
            used = status != RefreshTokenStatus.ACTIVE,
        )
    }

    /**
     * A read for the family/session the new row belongs to, then the atomic conditional update
     * that decides [RefreshTokenStore.RotateOutcome] — the `where` clause's own `status eq
     * active` is what makes a concurrent rotation of the same [oldHash] report
     * [RefreshTokenStore.RotateOutcome.ALREADY_ROTATED] instead of both callers succeeding.
     */
    override suspend fun rotate(
        oldHash: HashedToken,
        newHash: HashedToken,
        expiresAt: Instant,
        now: Instant,
    ): RefreshTokenStore.RotateOutcome {
        val old = RefreshTokensTable
            .selectAll()
            .where { RefreshTokensTable.hash eq oldHash.hash.revealed() }
            .singleOrNull()

        val consumed = old != null &&
            RefreshTokensTable.update({
                (RefreshTokensTable.hash eq oldHash.hash.revealed()) and
                    (RefreshTokensTable.status eq RefreshTokenStatus.ACTIVE.name.lowercase())
            }) {
                it[status] = RefreshTokenStatus.CONSUMED.name.lowercase()
                it[consumedAt] = instant.toColumn(now)
            } == 1

        return if (old == null || !consumed) {
            RefreshTokenStore.RotateOutcome.AlreadyRotated
        } else {
            RefreshTokensTable.insert {
                it[hash] = newHash.hash.revealed()
                it[familyId] = old[RefreshTokensTable.familyId]
                it[sessionId] = old[RefreshTokensTable.sessionId]
                it[pepperVersion] = newHash.pepperVersion
                it[status] = RefreshTokenStatus.ACTIVE.name.lowercase()
                it[issuedAt] = instant.toColumn(now)
                it[RefreshTokensTable.expiresAt] = instant.toColumn(expiresAt)
            }
            RefreshTokenStore.RotateOutcome.Rotated(SessionId(old[RefreshTokensTable.sessionId]))
        }
    }

    override suspend fun revokeAllFor(sessionId: SessionId) {
        RefreshTokensTable.update({
            (RefreshTokensTable.sessionId eq sessionId.value) and
                (RefreshTokensTable.status eq RefreshTokenStatus.ACTIVE.name.lowercase())
        }) {
            it[status] = RefreshTokenStatus.REVOKED.name.lowercase()
        }
    }

    override suspend fun deleteIssuedBefore(cutoff: Instant): Int =
        RefreshTokensTable.deleteWhere { issuedAt less instant.toColumn(cutoff) }
}
