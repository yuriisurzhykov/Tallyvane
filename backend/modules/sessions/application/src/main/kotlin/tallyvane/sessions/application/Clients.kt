package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration

/**
 * Hears the kind of client a version tells, and nothing else, for the code that must name what a version is for
 * without reaching into it.
 */
internal class Clients : LifetimeVersion.Record {
    private val heard = mutableListOf<ClientType>()

    fun of(version: LifetimeVersion): ClientType {
        heard.clear()
        version.writeTo(this)
        return heard.single()
    }

    override fun number(number: Int) = Unit

    override fun client(client: ClientType) {
        heard += client
    }

    override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) = Unit
}
