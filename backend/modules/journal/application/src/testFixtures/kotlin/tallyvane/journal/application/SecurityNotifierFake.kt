package tallyvane.journal.application

import tallyvane.journal.application.port.SecurityNotifier
import tallyvane.journal.domain.Entry

/**
 * [SecurityNotifier] that remembers each entry it was told, for the tests of the code that tells it.
 */
class SecurityNotifierFake : SecurityNotifier {
    private val told = mutableListOf<Entry>()

    /**
     * Every entry told so far, in order.
     */
    fun told(): List<Entry> = told.toList()

    override fun notify(entry: Entry) {
        told += entry
    }

    override fun toString(): String = "SecurityNotifierFake(${told.size})"
}
