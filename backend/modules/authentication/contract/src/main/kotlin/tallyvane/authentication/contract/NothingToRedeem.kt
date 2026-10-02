package tallyvane.authentication.contract

/**
 * There was nothing complete to take.
 */
public class NothingToRedeem : Redemption {
    override fun <T> reportTo(report: Redemption.Report<T>): T = report.nothingToRedeem()

    override fun equals(other: Any?): Boolean = other is NothingToRedeem

    override fun hashCode(): Int = NothingToRedeem::class.hashCode()

    override fun toString(): String = "NothingToRedeem"
}
