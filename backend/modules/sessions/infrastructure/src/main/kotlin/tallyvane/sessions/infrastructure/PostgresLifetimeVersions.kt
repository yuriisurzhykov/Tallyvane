package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.identity.contract.AccountId
import tallyvane.sessions.application.Change
import tallyvane.sessions.application.KeptLifetimes
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientLifetimes
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeRules
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * [LifetimeVersions] over the `sessions` schema in Postgres, inside the caller's transaction (ADR-052).
 *
 * [active] is one statement that reads the version in force for every kind of client: the last activation of each,
 * joined to its version, so a request pays one query however many kinds there are. The rules come back through
 * [LifetimeRules.restore], so what is kept is checked against the bounds the code sets today, and a row that falls
 * outside them fails here and not in a request's judgement of a session. A kind of client with no version kept is
 * judged by its starting lifetimes, and a kind this release does not know is left out.
 *
 * Nothing here updates or deletes: versions and activations are only inserted, and the schema's triggers refuse
 * anything else.
 *
 * ### Two administrators adding at once
 *
 * The next number is "the highest kept plus one", which two transactions would compute alike. [add] therefore takes a
 * transaction-scoped advisory lock named after the kind of client first, so the second waits for the first to commit
 * and then counts past it. The primary key on `(client_type, number)` is what would still refuse a duplicate if the
 * lock were ever left out.
 *
 * ### Two administrators activating at once
 *
 * The version in force is the one with the greatest activation identity, and the identity is drawn when the row is
 * inserted, not when it commits. [activate] takes the same lock, so an activation that returns has committed before
 * any other activation of the kind of client is inserted, and the version it asked for was in force until a later one
 * replaced it.
 */
internal class PostgresLifetimeVersions : LifetimeVersions {
    private val words = StoredDevices()

    override fun active(): LifetimeRules {
        val latest = LifetimeActivationsTable.id.max()
        val inForce = LifetimeActivationsTable.select(latest).groupBy(LifetimeActivationsTable.clientType)
        val rows = LifetimeActivationsTable
            .join(LifetimeVersionsTable, JoinType.INNER) {
                (LifetimeActivationsTable.clientType eq LifetimeVersionsTable.clientType) and
                    (LifetimeActivationsTable.number eq LifetimeVersionsTable.number)
            }
            .select(
                LifetimeVersionsTable.clientType,
                LifetimeVersionsTable.idleMillis,
                LifetimeVersionsTable.absoluteMillis,
                LifetimeVersionsTable.freshnessMillis,
            )
            .where { LifetimeActivationsTable.id inSubQuery inForce }
            .toList()
        return LifetimeRules.restore { record ->
            rows.forEach { row ->
                // A kind this release does not know belongs to a newer one; it is not this one's to judge.
                words.clientOrNull(row[LifetimeVersionsTable.clientType])?.let { client ->
                    record.lifetimes(
                        client,
                        row[LifetimeVersionsTable.idleMillis].milliseconds,
                        row[LifetimeVersionsTable.absoluteMillis].milliseconds,
                        row[LifetimeVersionsTable.freshnessMillis].milliseconds,
                    )
                }
            }
        }
    }

    override fun history(client: ClientType): KeptLifetimes {
        val clientWord = words.of(client)
        val made = LifetimeVersionsTable.selectAll()
            .where { LifetimeVersionsTable.clientType eq clientWord }
            .orderBy(LifetimeVersionsTable.number, SortOrder.DESC)
            .map { head ->
                KeptLifetimes.Entry(
                    versionOf(head),
                    head[LifetimeVersionsTable.madeBy]?.let(::AccountId),
                    head[LifetimeVersionsTable.comment],
                    head[LifetimeVersionsTable.createdAt],
                )
            }
        val latest = LifetimeActivationsTable.selectAll()
            .where { LifetimeActivationsTable.clientType eq clientWord }
            .orderBy(LifetimeActivationsTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
        return KeptLifetimes(
            made,
            latest?.let {
                KeptLifetimes.InForce(
                    it[LifetimeActivationsTable.number],
                    it[LifetimeActivationsTable.activatedBy]?.let(::AccountId),
                    it[LifetimeActivationsTable.reason],
                    it[LifetimeActivationsTable.activatedAt],
                )
            },
        )
    }

    override fun add(lifetimes: ClientLifetimes, change: Change, at: Instant): LifetimeVersion {
        val heard = Heard().also { lifetimes.writeTo(it) }
        val clientWord = words.of(heard.client())
        lockWritesOf(clientWord)
        val highest = LifetimeVersionsTable.select(LifetimeVersionsTable.number.max())
            .where { LifetimeVersionsTable.clientType eq clientWord }
            .single()[LifetimeVersionsTable.number.max()] ?: 0
        val told = ChangeHeard().also { change.writeTo(it) }
        LifetimeVersionsTable.insert {
            it[clientType] = clientWord
            it[number] = highest + 1
            it[idleMillis] = heard.idle().inWholeMilliseconds
            it[absoluteMillis] = heard.absolute().inWholeMilliseconds
            it[freshnessMillis] = heard.freshness().inWholeMilliseconds
            it[createdAt] = at
            it[madeBy] = told.author().value
            it[comment] = told.comment()
        }
        return versionOf(
            LifetimeVersionsTable.selectAll()
                .where {
                    (LifetimeVersionsTable.clientType eq clientWord) and
                        (LifetimeVersionsTable.number eq highest + 1)
                }
                .single(),
        )
    }

    override fun activate(version: LifetimeVersion, change: Change, at: Instant) {
        val heard = Heard().also { version.writeTo(it) }
        val clientWord = words.of(heard.client())
        val head = LifetimeVersionsTable.selectAll()
            .where {
                (LifetimeVersionsTable.clientType eq clientWord) and
                    (LifetimeVersionsTable.number eq heard.number())
            }
            .singleOrNull()
        check(head != null) {
            "Version ${heard.number()} of $clientWord is not kept, so it cannot be put in force. " +
                "Only a version obtained from LifetimeVersions.add or history can be activated."
        }
        check(Heard().also { versionOf(head).writeTo(it) }.told() == heard.told()) {
            "Version ${heard.number()} of $clientWord is kept, but the version given says something else " +
                "than the one kept. A version put in force must be the one that LifetimeVersions.add or " +
                "history handed out, not one built to look like it."
        }
        val told = ChangeHeard().also { change.writeTo(it) }
        lockWritesOf(clientWord)
        LifetimeActivationsTable.insert {
            it[clientType] = clientWord
            it[number] = heard.number()
            it[activatedAt] = at
            it[activatedBy] = told.author().value
            it[reason] = told.comment()
        }
    }

    override fun toString(): String = "PostgresLifetimeVersions(schema=sessions)"

    /**
     * Makes concurrent adds and activations for one kind of client take turns, until the transaction ends: the
     * activation that is last by its identity is the one in force, so it must also be the one that commits last.
     */
    private fun lockWritesOf(clientWord: String) {
        TransactionManager.current().exec(
            "select pg_advisory_xact_lock(hashtext(?))",
            listOf(TextColumnType() to "sessions.lifetime_versions.$clientWord"),
        ) { }
    }

    private fun versionOf(head: ResultRow): LifetimeVersion = LifetimeVersion.restore { record ->
        record.number(head[LifetimeVersionsTable.number])
        record.client(words.clientFrom(head[LifetimeVersionsTable.clientType]))
        record.lifetimes(
            head[LifetimeVersionsTable.idleMillis].milliseconds,
            head[LifetimeVersionsTable.absoluteMillis].milliseconds,
            head[LifetimeVersionsTable.freshnessMillis].milliseconds,
        )
    }

    /**
     * Hears everything a version or lifetimes tell, so that adding and activating can name them (kind of client and
     * number) and can tell whether a version is the one that is kept: two versions are the same when they say the
     * same words.
     */
    private class Heard : LifetimeVersion.Record {
        private val clients = mutableListOf<ClientType>()
        private val numbers = mutableListOf<Int>()
        private val limits = mutableListOf<Triple<Duration, Duration, Duration>>()
        private val lines = mutableListOf<String>()

        fun client(): ClientType = clients.single()

        fun number(): Int = numbers.single()

        fun idle(): Duration = limits.single().first

        fun absolute(): Duration = limits.single().second

        fun freshness(): Duration = limits.single().third

        fun told(): List<String> = lines.toList()

        override fun number(number: Int) {
            numbers += number
            lines += "number $number"
        }

        override fun client(client: ClientType) {
            clients += client
            lines += "client $client"
        }

        override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
            limits += Triple(idle, absolute, freshness)
            lines += "lifetimes $idle $absolute $freshness"
        }
    }

    /**
     * Hears who made a change and the words they gave.
     */
    private class ChangeHeard : Change.Record {
        private val authors = mutableListOf<AccountId>()
        private val comments = mutableListOf<String>()

        fun author(): AccountId = authors.single()

        fun comment(): String = comments.single()

        override fun change(author: AccountId, comment: String) {
            authors += author
            comments += comment
        }
    }
}
