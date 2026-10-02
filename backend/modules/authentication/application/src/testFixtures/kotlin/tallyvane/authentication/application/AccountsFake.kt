package tallyvane.authentication.application

import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Accounts
import tallyvane.identity.contract.NameRefused
import tallyvane.identity.contract.Registered
import tallyvane.identity.contract.Registrant
import tallyvane.identity.contract.Registration
import kotlin.uuid.Uuid

/**
 * [Accounts] of a few people, for tests of what `authentication` does with the answers. What a real
 * `identity` accepts as a name is its own tests' business; this one refuses only a blank.
 */
class AccountsFake : Accounts {
    private val bySubject = mutableMapOf<String, AccountId>()
    private var sequence = 0

    fun knows(subject: String) {
        bySubject[subject] = nextId()
    }

    fun knowing(subject: String): Boolean = subject in bySubject

    override fun withGoogle(subject: String): AccountId? = bySubject[subject]

    override fun register(registrant: Registrant): Registration {
        val told = mutableListOf<Pair<String, String>>()
        registrant.writeTo { subject, name, _, _ -> told += subject to name }
        val (subject, name) = told.single()
        return when {
            name.isBlank() -> NameRefused()
            else -> Registered(bySubject.getOrPut(subject) { nextId() })
        }
    }

    private fun nextId() = AccountId(Uuid.parse("00000000-0000-7000-8000-%012d".format(++sequence)))

    override fun toString(): String = "AccountsFake(${bySubject.size})"
}
