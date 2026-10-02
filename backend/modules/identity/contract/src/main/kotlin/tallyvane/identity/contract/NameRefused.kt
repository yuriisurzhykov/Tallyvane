package tallyvane.identity.contract

/**
 * The chosen name was not acceptable, and nothing was created.
 */
public class NameRefused : Registration {
    override fun <T> reportTo(report: Registration.Report<T>): T = report.nameRefused()

    override fun equals(other: Any?): Boolean = other is NameRefused

    override fun hashCode(): Int = NameRefused::class.hashCode()

    override fun toString(): String = "NameRefused"
}
