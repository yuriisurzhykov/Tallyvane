package tallyvane.identity.contract

/**
 * The account exists: made by this registration, or found because the same person registered before.
 */
public class Registered(private val account: AccountId) : Registration {
    override fun <T> reportTo(report: Registration.Report<T>): T = report.registered(account)

    override fun equals(other: Any?): Boolean = other is Registered && other.account == account

    override fun hashCode(): Int = account.hashCode()

    override fun toString(): String = "Registered($account)"
}
