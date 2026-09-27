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
internal class RefreshTokenStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    RefreshTokenStore {
    private val instant = InstantColumn()
    private val table: RefreshTokenRowsTable = when (realm) {
        IdentityRealm.USER -> RefreshTokensTable
        IdentityRealm.ADMIN -> AdminRefreshTokensTable
    }

    override suspend fun issueFirst(
        sessionId: SessionId,
        familyId: TokenFamilyId,
        hash: HashedToken,
        expiresAt: Instant,
        issuedAt: Instant,
    ) {
        table.insert {
            it[table.hash] = hash.hash.revealed()
            it[table.familyId] = familyId.value
            it[table.sessionId] = sessionId.value
            it[table.pepperVersion] = hash.pepperVersion
            it[table.status] = RefreshTokenStatus.ACTIVE.name.lowercase()
            it[table.issuedAt] = instant.toColumn(issuedAt)
            it[table.expiresAt] = instant.toColumn(expiresAt)
        }
    }

    override suspend fun stateOf(hash: HashedToken): TokenFamilyState? {
        val row = table
            .selectAll()
            .where { table.hash eq hash.hash.revealed() }
            .singleOrNull() ?: return null
        val status = RefreshTokenStatus.valueOf(row[table.status].uppercase())
        return TokenFamilyState(
            sessionId = SessionId(row[table.sessionId]),
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
        val old = table
            .selectAll()
            .where { table.hash eq oldHash.hash.revealed() }
            .singleOrNull()

        val consumed = old != null &&
            table.update({
                (table.hash eq oldHash.hash.revealed()) and
                    (table.status eq RefreshTokenStatus.ACTIVE.name.lowercase())
            }) {
                it[table.status] = RefreshTokenStatus.CONSUMED.name.lowercase()
                it[table.consumedAt] = instant.toColumn(now)
            } == 1

        return if (old == null || !consumed) {
            RefreshTokenStore.RotateOutcome.AlreadyRotated
        } else {
            table.insert {
                it[table.hash] = newHash.hash.revealed()
                it[table.familyId] = old[table.familyId]
                it[table.sessionId] = old[table.sessionId]
                it[table.pepperVersion] = newHash.pepperVersion
                it[table.status] = RefreshTokenStatus.ACTIVE.name.lowercase()
                it[table.issuedAt] = instant.toColumn(now)
                it[table.expiresAt] = instant.toColumn(expiresAt)
            }
            RefreshTokenStore.RotateOutcome.Rotated(SessionId(old[table.sessionId]))
        }
    }

    override suspend fun revokeAllFor(sessionId: SessionId) {
        table.update({
            (table.sessionId eq sessionId.value) and
                (table.status eq RefreshTokenStatus.ACTIVE.name.lowercase())
        }) {
            it[table.status] = RefreshTokenStatus.REVOKED.name.lowercase()
        }
    }

    override suspend fun deleteIssuedBefore(cutoff: Instant): Int =
        table.deleteWhere { table.issuedAt less instant.toColumn(cutoff) }
}
