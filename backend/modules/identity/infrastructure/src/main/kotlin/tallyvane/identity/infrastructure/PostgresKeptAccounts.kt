package tallyvane.identity.infrastructure

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.identity.application.AccountAdded
import tallyvane.identity.application.Profile
import tallyvane.identity.application.port.KeptAccounts
import tallyvane.identity.domain.Account
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val CLAIM_SUBJECT = """
    insert into identity.external_identities (provider, subject, account_id, linked_at)
    values (?, ?, ?, ?)
    on conflict (provider, subject) do nothing
    returning account_id
"""

/**
 * [KeptAccounts] over the `identity` schema in Postgres, inside the caller's transaction (ADR-052).
 *
 * ### Two registrations of one person at once
 *
 * [add] writes the account row first and then claims the Google subject with `ON CONFLICT DO
 * NOTHING`. When the claim inserts nothing, another registration holds the subject: this one answers
 * [AccountAdded.SubjectTaken], and the account row it wrote must not survive, so it is deleted again in
 * the same transaction. The primary key on `(provider, subject)` is what decides who won; nothing here
 * reads first and writes second. A registration that arrives while the other is still open waits on it,
 * as Postgres makes a conflicting insert wait, and sees the outcome once it commits.
 */
internal class PostgresKeptAccounts : KeptAccounts {
    override fun withGoogle(subject: String): Uuid? = ExternalIdentitiesTable.selectAll()
        .where { (ExternalIdentitiesTable.provider eq GOOGLE) and (ExternalIdentitiesTable.subject eq subject) }
        .singleOrNull()
        ?.get(ExternalIdentitiesTable.accountId)

    override fun isAdministrator(id: Uuid): Boolean =
        !AdminsTable.selectAll().where { AdminsTable.accountId eq id }.empty()

    override fun profileOf(id: Uuid): Profile? = AccountsTable.selectAll()
        .where { AccountsTable.id eq id }
        .singleOrNull()
        ?.let { Profile(id, it[AccountsTable.displayName]) }

    override fun add(account: Account): AccountAdded {
        val outcomes = mutableListOf<AccountAdded>()
        account.writeTo { id, googleSubject, displayName, email, registeredAt ->
            AccountsTable.insert {
                it[AccountsTable.id] = id
                it[AccountsTable.displayName] = displayName
                it[AccountsTable.email] = email
                it[AccountsTable.registeredAt] = registeredAt
                it[consentedAt] = registeredAt
            }
            outcomes += claimed(id, googleSubject, registeredAt)
        }
        return outcomes.single()
    }

    private fun claimed(id: Uuid, subject: String, at: Instant): AccountAdded {
        val claim = ExternalIdentitiesTable
        val inserted = TransactionManager.current().exec(
            CLAIM_SUBJECT,
            listOf<Pair<IColumnType<*>, Any?>>(
                claim.provider.columnType to GOOGLE,
                claim.subject.columnType to subject,
                claim.accountId.columnType to id,
                claim.linkedAt.columnType to at,
            ),
            // `returning` makes it a query to the driver, whatever word the statement starts with.
            StatementType.SELECT,
        ) { rows -> rows.next() }
        return when (inserted) {
            true -> AccountAdded.Added
            else -> AccountAdded.SubjectTaken.also { withdraw(id) }
        }
    }

    private fun withdraw(id: Uuid) {
        TransactionManager.current().exec(
            WITHDRAW,
            listOf<Pair<IColumnType<*>, Any?>>(AccountsTable.id.columnType to id),
        )
    }

    override fun toString(): String = "PostgresKeptAccounts(schema=identity)"

    private companion object {
        /**
         * The word the migration's `check` constraint accepts for Google.
         */
        const val GOOGLE = "google"

        const val WITHDRAW = "delete from identity.accounts where id = ?"
    }
}
