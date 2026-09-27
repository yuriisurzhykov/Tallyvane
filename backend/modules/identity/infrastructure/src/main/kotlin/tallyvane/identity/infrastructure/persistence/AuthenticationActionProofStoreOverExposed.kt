package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationActionProof
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant

internal class AuthenticationActionProofStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    AuthenticationActionProofStore {
    private val instant = InstantColumn()
    private val table: AuthenticationActionProofRowsTable = when (realm) {
        IdentityRealm.USER -> AuthenticationActionProofsTable
        IdentityRealm.ADMIN -> AdminAuthenticationActionProofsTable
    }

    override suspend fun save(proof: AuthenticationActionProof) {
        table.insert {
            it[table.hash] = proof.token.hash.revealed()
            it[table.pepperVersion] = proof.token.pepperVersion
            it[table.userId] = proof.userId.value
            it[table.sessionId] = proof.sessionId.value
            it[table.action] = proof.action.name
            it[table.policyVersion] = proof.policyVersion
            it[table.schemeId] = proof.schemeId
            it[table.assuranceRank] = proof.assuranceRank
            it[table.expiresAt] = instant.toColumn(proof.expiresAt)
            it[table.consumedAt] = proof.consumedAt?.let(instant::toColumn)
        }
    }

    override suspend fun consume(
        token: HashedToken,
        userId: UserId,
        sessionId: SessionId,
        action: AuthenticationAction,
        policyVersion: Long,
        now: Instant,
    ): Boolean = table.update({
        (table.hash eq token.hash.revealed()) and
            (table.pepperVersion eq token.pepperVersion) and
            (table.userId eq userId.value) and
            (table.sessionId eq sessionId.value) and
            (table.action eq action.name) and
            (table.policyVersion eq policyVersion) and
            (table.consumedAt.isNull()) and
            (table.expiresAt greater instant.toColumn(now))
    }) {
        it[table.consumedAt] = instant.toColumn(now)
    } == 1

    override suspend fun deleteAllFor(userId: UserId) {
        table.deleteWhere { table.userId eq userId.value }
    }
}
