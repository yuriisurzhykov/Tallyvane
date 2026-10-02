package tallyvane.identity.application.port

import tallyvane.identity.application.AccountAdded
import tallyvane.identity.domain.Account
import kotlin.uuid.Uuid

/**
 * Where accounts are kept.
 *
 * Both methods run inside the caller's transaction and block on the database (ADR-058).
 */
public interface KeptAccounts {
    /**
     * The id of the account Google knows by [subject], or null when there is none.
     */
    public fun withGoogle(subject: String): Uuid?

    /**
     * Keeps [account], unless an account for the same Google subject is already kept, in which case
     * nothing changes and the answer is [AccountAdded.SubjectTaken]. Two registrations of one person
     * that race each other end with one account, not with an error.
     */
    public fun add(account: Account): AccountAdded
}
