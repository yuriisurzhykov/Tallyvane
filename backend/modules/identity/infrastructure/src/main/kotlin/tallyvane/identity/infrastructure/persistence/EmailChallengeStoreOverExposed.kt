package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Per-slot row locks serialize resends and guesses across processes, including first issuance.
 */
internal class EmailChallengeStoreOverExposed : EmailChallengeStore {
    private val instant = InstantColumn()

    override suspend fun issue(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        val inserted = EmailChallengesTable.insertIgnore {
            it[id] = challenge.id
            it[email] = challenge.email.value
            it[purpose] = challenge.purpose.name
            it[binding] = challenge.binding
            it[EmailChallengesTable.hash] = hash.revealed()
            it[expiresAt] = instant.toColumn(challenge.expiresAt)
            it[EmailChallengesTable.resendAt] = instant.toColumn(resendAt)
            it[remainingAttempts] = maxAttempts
            it[consumed] = false
        }
        return inserted.insertedCount == 1 || replaceExisting(challenge, hash, now, resendAt, maxAttempts)
    }

    private fun replaceExisting(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        val slot = (EmailChallengesTable.email eq challenge.email.value) and
            (EmailChallengesTable.purpose eq challenge.purpose.name) and
            (EmailChallengesTable.binding eq challenge.binding)
        val previous = EmailChallengesTable.selectAll().where { slot }.forUpdate().single()
        if (instant.toDomain(previous[EmailChallengesTable.resendAt]) > now) return false
        EmailChallengesTable.update({ slot }) {
            it[id] = challenge.id
            it[EmailChallengesTable.hash] = hash.revealed()
            it[expiresAt] = instant.toColumn(challenge.expiresAt)
            it[EmailChallengesTable.resendAt] = instant.toColumn(resendAt)
            it[remainingAttempts] = maxAttempts
            it[consumed] = false
        }
        return true
    }

    override suspend fun find(id: Uuid): EmailChallenge? = EmailChallengesTable.selectAll()
        .where { EmailChallengesTable.id eq id }.singleOrNull()?.toChallenge()

    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean {
        val row = EmailChallengesTable.selectAll().where { EmailChallengesTable.id eq id }
            .forUpdate().singleOrNull()
        return row?.takeIf { it.isUsableAt(now) }?.let { current ->
            val matches = Secret(current[EmailChallengesTable.hash]) == hash
            EmailChallengesTable.update({ EmailChallengesTable.id eq id }) {
                it[remainingAttempts] = current[EmailChallengesTable.remainingAttempts] - 1
                it[consumed] = matches
            }
            matches
        } ?: false
    }

    private fun ResultRow.isUsableAt(now: Instant): Boolean = !this[EmailChallengesTable.consumed] &&
        this[EmailChallengesTable.remainingAttempts] > 0 &&
        instant.toDomain(this[EmailChallengesTable.expiresAt]) > now

    override suspend fun revoke(id: Uuid) {
        EmailChallengesTable.update({ EmailChallengesTable.id eq id }) { it[consumed] = true }
    }

    private fun ResultRow.toChallenge(): EmailChallenge = EmailChallenge(
        this[EmailChallengesTable.id],
        Email(this[EmailChallengesTable.email]),
        EmailChallengePurpose.valueOf(this[EmailChallengesTable.purpose]),
        this[EmailChallengesTable.binding],
        instant.toDomain(this[EmailChallengesTable.expiresAt]),
    )
}
