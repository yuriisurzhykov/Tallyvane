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
    private val profiles = mutableMapOf<Uuid, Profile>()

    override fun withGoogle(subject: String): Uuid? = bySubject[subject]

    override fun profileOf(id: Uuid): Profile? = profiles[id]

    override fun add(account: Account): AccountAdded {
        val told = mutableListOf<Triple<String, Uuid, String>>()
        account.writeTo { id, subject, name, _, _ -> told += Triple(subject, id, name) }
        val (subject, id, name) = told.single()
        return if (subject in bySubject) {
            AccountAdded.SubjectTaken
        } else {
            bySubject[subject] = id
            profiles[id] = Profile(id, name)
            AccountAdded.Added
        }
    }

    override fun toString(): String = "KeptAccountsFake(kept=${bySubject.size})"
}
