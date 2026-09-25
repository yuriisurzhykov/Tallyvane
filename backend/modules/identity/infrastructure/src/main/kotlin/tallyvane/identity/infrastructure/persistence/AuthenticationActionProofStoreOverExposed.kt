package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.greater
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

internal class AuthenticationActionProofStoreOverExposed : AuthenticationActionProofStore {
    private val instant = InstantColumn()

    override suspend fun save(proof: AuthenticationActionProof) {
        AuthenticationActionProofsTable.insert {
            it[hash] = proof.token.hash.revealed()
            it[pepperVersion] = proof.token.pepperVersion
            it[userId] = proof.userId.value
            it[sessionId] = proof.sessionId.value
            it[action] = proof.action.name
            it[policyVersion] = proof.policyVersion
            it[schemeId] = proof.schemeId
            it[assuranceRank] = proof.assuranceRank
            it[expiresAt] = instant.toColumn(proof.expiresAt)
            it[consumedAt] = proof.consumedAt?.let(instant::toColumn)
        }
    }

    override suspend fun consume(
        token: HashedToken,
        userId: UserId,
        sessionId: SessionId,
        action: AuthenticationAction,
        policyVersion: Long,
        now: Instant,
    ): Boolean = AuthenticationActionProofsTable.update({
        (AuthenticationActionProofsTable.hash eq token.hash.revealed()) and
            (AuthenticationActionProofsTable.pepperVersion eq token.pepperVersion) and
            (AuthenticationActionProofsTable.userId eq userId.value) and
            (AuthenticationActionProofsTable.sessionId eq sessionId.value) and
            (AuthenticationActionProofsTable.action eq action.name) and
            (AuthenticationActionProofsTable.policyVersion eq policyVersion) and
            (AuthenticationActionProofsTable.consumedAt.isNull()) and
            (AuthenticationActionProofsTable.expiresAt greater instant.toColumn(now))
    }) {
        it[consumedAt] = instant.toColumn(now)
    } == 1

    override suspend fun deleteAllFor(userId: UserId) {
        AuthenticationActionProofsTable.deleteWhere { AuthenticationActionProofsTable.userId eq userId.value }
    }
}
