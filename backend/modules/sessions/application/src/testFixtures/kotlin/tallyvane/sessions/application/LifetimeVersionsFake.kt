package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.sessions.application.port.LifetimeVersions
import tallyvane.sessions.domain.ClientLifetimes
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeRules
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * [LifetimeVersions] that keeps them in lists, for tests of the code that uses the port (ADR-044).
 *
 * Starts as the migrations leave the database: version 1 of each kind of client, made by nobody, in force. A
 * browser's is a day, a week and five minutes of freshness; an administrator's is an hour, eight hours and five
 * minutes.
 *
 * [LifetimeVersionsFakeSpec] holds it to the same suite as the adapter over Postgres.
 */
class LifetimeVersionsFake : LifetimeVersions {
    private val versions = mutableMapOf<ClientType, MutableList<Made>>()
    private val activations = mutableMapOf<ClientType, MutableList<Activated>>()

    init {
        seed(CheckedLifetimes(ClientType.Browser, 1.days, 7.days, 5.minutes).lifetimes())
        seed(CheckedLifetimes(ClientType.Admin, 1.hours, 8.hours, 5.minutes).lifetimes())
    }

    override fun active(): LifetimeRules = LifetimeRules.restore { rules ->
        activations.values.mapNotNull { it.lastOrNull() }.forEach { activated ->
            activated.version.writeTo(Telling(rules))
        }
    }

    override fun history(client: ClientType): KeptLifetimes = KeptLifetimes(
        versions[client].orEmpty().reversed().map { KeptLifetimes.Entry(it.version, it.madeBy, it.comment, it.at) },
        activations[client]?.lastOrNull()?.let {
            KeptLifetimes.InForce(NumberOf(it.version).number(), it.by, it.reason, it.at)
        },
    )

    override fun add(lifetimes: ClientLifetimes, change: Change, at: Instant): LifetimeVersion {
        val client = ClientOf(lifetimes).client()
        val kept = versions.getOrPut(client) { mutableListOf() }
        val story = ChangeStory(change)
        return LifetimeVersion(kept.size + 1, lifetimes).also {
            kept += Made(it, story.author(), story.comment(), at)
        }
    }

    override fun activate(version: LifetimeVersion, change: Change, at: Instant) {
        val client = ClientOf(version).client()
        check(versions[client].orEmpty().any { VersionStory(it.version) == VersionStory(version) }) {
            "No version kept by this fake says what the version given says, so it cannot be activated."
        }
        val story = ChangeStory(change)
        activations.getOrPut(client) { mutableListOf() } += Activated(version, story.author(), story.comment(), at)
    }

    /**
     * Puts a version in force that gives a browser [idle], [absolute] and [freshness], as an activation would.
     */
    fun activate(idle: Duration, absolute: Duration, freshness: Duration = 5.minutes) {
        val change = SampleChange().change()
        val kept = add(CheckedLifetimes(ClientType.Browser, idle, absolute, freshness).lifetimes(), change, EPOCH)
        activate(kept, change, EPOCH)
    }

    override fun toString(): String = "LifetimeVersionsFake(versions=${versions.values.sumOf { it.size }})"

    private fun seed(lifetimes: ClientLifetimes) {
        val client = ClientOf(lifetimes).client()
        val version = LifetimeVersion(1, lifetimes)
        versions[client] = mutableListOf(Made(version, null, null, EPOCH))
        activations[client] = mutableListOf(Activated(version, null, null, EPOCH))
    }

    /**
     * Tells [rules] the lifetimes of the version it is told, which says the kind of client before the numbers.
     */
    private class Telling(private val rules: LifetimeRules.Record) : LifetimeVersion.Record {
        private var kind: ClientType? = null

        override fun number(number: Int) = Unit

        override fun client(client: ClientType) {
            kind = client
        }

        override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
            rules.lifetimes(checkNotNull(kind), idle, absolute, freshness)
        }
    }

    private class Made(val version: LifetimeVersion, val madeBy: AccountId?, val comment: String?, val at: Instant)

    private class Activated(val version: LifetimeVersion, val by: AccountId?, val reason: String?, val at: Instant)

    private companion object {
        val EPOCH: Instant = Instant.parse("2026-10-01T09:00:00Z")
    }
}
