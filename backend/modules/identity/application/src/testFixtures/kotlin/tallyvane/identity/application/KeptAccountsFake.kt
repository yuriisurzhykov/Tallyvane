package tallyvane.identity.application

import tallyvane.identity.application.port.KeptAccounts
import tallyvane.identity.domain.Account
import kotlin.uuid.Uuid

/**
 * [KeptAccounts] in a map, for tests of the code that uses the port, in this module and in
 * `authentication` (ADR-044). [KeptAccountsFakeSpec] holds it to the suite the adapter over Postgres
 * passes.
 */
class KeptAccountsFake : KeptAccounts {
    private val bySubject = mutableMapOf<String, Uuid>()

    override fun withGoogle(subject: String): Uuid? = bySubject[subject]

    override fun add(account: Account): AccountAdded {
        val told = mutableListOf<Pair<String, Uuid>>()
        account.writeTo { id, subject, _, _, _ -> told += subject to id }
        val (subject, id) = told.single()
        return if (subject in bySubject) {
            AccountAdded.SubjectTaken
        } else {
            bySubject[subject] = id
            AccountAdded.Added
        }
    }

    override fun toString(): String = "KeptAccountsFake(kept=${bySubject.size})"
}
