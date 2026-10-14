package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration

/**
 * Hears the number a version tells, and nothing else, for the code that must name a version without reaching
 * into it.
 */
internal class Numbered : LifetimeVersion.Record {
    private val heard = mutableListOf<Int>()

    fun of(version: LifetimeVersion): Int {
        heard.clear()
        version.writeTo(this)
        return heard.single()
    }

    override fun number(number: Int) {
        heard += number
    }

    override fun client(client: ClientType) = Unit

    override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) = Unit
}
