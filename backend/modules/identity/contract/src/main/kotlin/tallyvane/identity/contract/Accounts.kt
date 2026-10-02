package tallyvane.identity.contract

/**
 * Who people are, as `identity` answers it to its neighbours (ADR-076).
 *
 * `authentication` asks this to learn whose account a Google sign-in proved, and to create the
 * account when the person is new. Nothing else about an account is published yet: the session slice
 * reads only the [AccountId], and the profile and the capabilities of §8.3 arrive with the code that
 * reads them.
 *
 * Both methods run inside the caller's transaction and never open one (`TransactionRunner`, ADR-052),
 * so "create the account" and "remember the attempt" commit or roll back together.
 */
public interface Accounts {
    /**
     * The account Google knows by [subject], its `sub` claim, or null when nobody has registered with it.
     *
     * Keyed by the subject and never by the email address (ADR-077): an address can be recycled or
     * changed at Google, a subject cannot.
     */
    public fun withGoogle(subject: String): AccountId?

    /**
     * Creates the account [registrant] describes, or finds the one already created for the same Google
     * subject: a person who submits the welcome form twice has one account.
     */
    public fun register(registrant: Registrant): Registration
}
