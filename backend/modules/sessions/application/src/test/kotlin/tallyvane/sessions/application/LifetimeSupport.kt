package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId
import tallyvane.sessions.domain.ClientType
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What the specs of the lifetime use cases read off an outcome.
 */
internal class LifetimeSupport {
    /**
     * The versions of a listing, as lines.
     */
    fun listed(outcome: LifetimeVersionsListed): List<String> {
        val lines = mutableListOf<String>()
        (outcome as LifetimeVersionsListed.Listed).writeTo(
            object : KeptLifetimes.Record {
                override fun kept(madeBy: AccountId?, comment: String?, madeAt: Instant) {
                    lines += "kept by ${madeBy?.value} because '$comment'"
                }

                override fun inForce(since: Instant, by: AccountId?, reason: String?) {
                    lines += "in force since $since by ${by?.value} because '$reason'"
                }

                override fun number(number: Int) {
                    lines += "number $number"
                }

                override fun client(client: ClientType) = Unit

                override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
                    lines += "lifetimes $idle $absolute $freshness"
                }
            },
        )
        return lines
    }

    /**
     * The sessions live and the sessions that would end, of a preview.
     */
    fun previewed(outcome: LifetimeVersionPreviewed): Pair<Int, Int> {
        val told = mutableListOf<Pair<Int, Int>>()
        (outcome as LifetimeVersionPreviewed.Previewed).writeTo { sessions, wouldEnd -> told += sessions to wouldEnd }
        return told.single()
    }

    /**
     * The faults an outcome tells, as `field code` pairs.
     */
    fun faults(write: (Fault.Record) -> Unit): List<String> {
        val told = mutableListOf<String>()
        write(Fault.Record { field, code, _ -> told += "$field $code" })
        return told
    }

    /**
     * The proposal for lifetimes of a browser, which a case changes one thing in.
     */
    fun proposal(
        client: String = "browser",
        idleMillis: Long = DAY,
        absoluteMillis: Long = WEEK,
        freshnessMillis: Long = FIVE_MINUTES,
    ): ProposedLifetimes = ProposedLifetimes(client, idleMillis, absoluteMillis, freshnessMillis)

    companion object {
        const val DAY = 86_400_000L
        const val WEEK = 604_800_000L
        const val HOUR = 3_600_000L
        const val FIVE_MINUTES = 300_000L
    }
}
