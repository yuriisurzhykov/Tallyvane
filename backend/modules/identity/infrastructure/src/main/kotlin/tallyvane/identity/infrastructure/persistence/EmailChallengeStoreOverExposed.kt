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
internal class EmailChallengeStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    EmailChallengeStore {
    private val instant = InstantColumn()
    private val table: EmailChallengeRowsTable = when (realm) {
        IdentityRealm.USER -> EmailChallengesTable
        IdentityRealm.ADMIN -> AdminEmailChallengesTable
    }

    override suspend fun issue(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        val inserted = table.insertIgnore {
            it[id] = challenge.id
            it[email] = challenge.email.value
            it[purpose] = challenge.purpose.name
            it[binding] = challenge.binding
            it[table.hash] = hash.revealed()
            it[expiresAt] = instant.toColumn(challenge.expiresAt)
            it[table.resendAt] = instant.toColumn(resendAt)
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
        val slot = (table.email eq challenge.email.value) and
            (table.purpose eq challenge.purpose.name) and
            (table.binding eq challenge.binding)
        val previous = table.selectAll().where { slot }.forUpdate().single()
        if (instant.toDomain(previous[table.resendAt]) > now) return false
        table.update({ slot }) {
            it[id] = challenge.id
            it[table.hash] = hash.revealed()
            it[expiresAt] = instant.toColumn(challenge.expiresAt)
            it[table.resendAt] = instant.toColumn(resendAt)
            it[remainingAttempts] = maxAttempts
            it[consumed] = false
        }
        return true
    }

    override suspend fun find(id: Uuid): EmailChallenge? = table.selectAll()
        .where { table.id eq id }.singleOrNull()?.toChallenge()

    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean {
        val row = table.selectAll().where { table.id eq id }
            .forUpdate().singleOrNull()
        return row?.takeIf { it.isUsableAt(now) }?.let { current ->
            val matches = Secret(current[table.hash]) == hash
            table.update({ table.id eq id }) {
                it[table.remainingAttempts] = current[table.remainingAttempts] - 1
                it[table.consumed] = matches
            }
            matches
        } ?: false
    }

    private fun ResultRow.isUsableAt(now: Instant): Boolean = !this[table.consumed] &&
        this[table.remainingAttempts] > 0 &&
        instant.toDomain(this[table.expiresAt]) > now

    override suspend fun revoke(id: Uuid) {
        table.update({ table.id eq id }) { it[table.consumed] = true }
    }

    private fun ResultRow.toChallenge(): EmailChallenge = EmailChallenge(
        this[table.id],
        Email(this[table.email]),
        EmailChallengePurpose.valueOf(this[table.purpose]),
        this[table.binding],
        instant.toDomain(this[table.expiresAt]),
    )
}
