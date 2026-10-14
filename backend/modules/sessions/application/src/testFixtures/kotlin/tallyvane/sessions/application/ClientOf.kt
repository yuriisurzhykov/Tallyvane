package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientLifetimes
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration

/**
 * Asks lifetimes or a version which kind of client they are for, the only way there is: by listening to them.
 */
class ClientOf private constructor(private val heard: List<ClientType>) {
    constructor(lifetimes: ClientLifetimes) : this(
        mutableListOf<ClientType>().also { clients -> lifetimes.writeTo(Hearing(clients)) },
    )

    constructor(version: LifetimeVersion) : this(
        mutableListOf<ClientType>().also { clients -> version.writeTo(Hearing(clients)) },
    )

    fun client(): ClientType = heard.single()

    private class Hearing(private val clients: MutableList<ClientType>) : LifetimeVersion.Record {
        override fun number(number: Int) = Unit

        override fun client(client: ClientType) {
            clients += client
        }

        override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) = Unit
    }
}
