package tallyvane.identity.application

import tallyvane.identity.application.port.KeptAccounts
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Accounts
import tallyvane.identity.contract.NameRefused
import tallyvane.identity.contract.Registered
import tallyvane.identity.contract.Registrant
import tallyvane.identity.contract.Registration
import tallyvane.identity.domain.Account
import tallyvane.identity.domain.DisplayName
import tallyvane.platform.kernel.IdGenerator
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * How `identity` answers [Accounts]: through the accounts it keeps, with the rules of its domain.
 *
 * Opens no transaction, as the contract promises; the caller's is the one everything runs in.
 */
public class AccountDirectory(private val kept: KeptAccounts, private val ids: IdGenerator) : Accounts {
    override fun withGoogle(subject: String): AccountId? = kept.withGoogle(subject)?.let(::AccountId)

    override fun register(registrant: Registrant): Registration {
        val told = Told().also { registrant.writeTo(it) }
        val name = told.name()
        val existing = told.existingIn(kept)
        return when {
            name == null -> NameRefused()
            existing != null -> Registered(AccountId(existing))
            else -> Registered(AccountId(told.keptAs(name)))
        }
    }

    override fun toString(): String = "AccountDirectory(kept=$kept)"

    /**
     * What a registrant told, on its way to becoming an account.
     */
    private inner class Told : Registrant.Record {
        private val subjects = mutableListOf<String>()
        private val names = mutableListOf<String>()
        private val emails = mutableListOf<String>()
        private val consents = mutableListOf<Instant>()

        override fun registrant(googleSubject: String, displayName: String, email: String, consentedAt: Instant) {
            subjects += googleSubject
            names += displayName
            emails += email
            consents += consentedAt
        }

        /**
         * The name the registrant chose, if `identity` accepts it.
         */
        fun name(): DisplayName? = DisplayName.of(names.single())

        /**
         * The account already kept for the same person.
         */
        fun existingIn(kept: KeptAccounts): Uuid? = kept.withGoogle(subjects.single())

        /**
         * Keeps a new account called [name] and answers its id, or the id of the account a registration
         * of the same person racing this one kept first.
         */
        fun keptAs(name: DisplayName): Uuid {
            val id = ids.next()
            val account = Account(id, subjects.single(), name, emails.single(), consents.single())
            return when (kept.add(account)) {
                AccountAdded.Added -> id
                AccountAdded.SubjectTaken -> checkNotNull(existingIn(kept)) {
                    "An account for this Google subject was kept a moment ago and cannot be found now. " +
                        "Accounts are not deleted during a registration; look for something removing rows."
                }
            }
        }
    }
}
