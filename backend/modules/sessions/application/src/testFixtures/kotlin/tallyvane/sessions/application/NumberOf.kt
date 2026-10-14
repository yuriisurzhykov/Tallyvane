package tallyvane.sessions.application

import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.LifetimeVersion
import kotlin.time.Duration

/**
 * Asks a version which number it has, the only way there is: by listening to it [LifetimeVersion.writeTo].
 */
class NumberOf(version: LifetimeVersion) {
    private val heard = mutableListOf<Int>().also { numbers ->
        version.writeTo(
            object : LifetimeVersion.Record {
                override fun number(number: Int) {
                    numbers += number
                }

                override fun client(client: ClientType) = Unit

                override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) = Unit
            },
        )
    }

    fun number(): Int = heard.single()
}
