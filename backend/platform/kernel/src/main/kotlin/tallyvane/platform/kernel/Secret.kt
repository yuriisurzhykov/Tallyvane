package tallyvane.platform.kernel

/**
 * A value that must not appear in a log line, an exception message or a crash dump.
 *
 * Wrap anything a deploy supplies as a credential — a database password, a service token — and the
 * enclosing object becomes safe to print: `toString()` yields `***`, including through the generated
 * `toString()` of a `data class` that holds one.
 *
 * `==` is safe to use on two of these: the comparison examines every character of both values rather
 * than stopping at the first difference, so it does not reveal through timing how much of a guess was
 * right. It does compare lengths first, and that reveals the length — a length is not the secret, and
 * concealing it would cost a hash on every comparison.
 *
 * It validates nothing. Whether an empty or short value is acceptable belongs to whatever reads that
 * particular setting, because the answer differs per setting.
 */
public class Secret(private val value: String) {
    /**
     * The value, for the one place that has to hand it to a driver.
     *
     * Named to be conspicuous at a call site: `access.password.revealed()` reads as a decision,
     * where `access.password` would read as an accident.
     */
    public fun revealed(): String = value

    override fun equals(other: Any?): Boolean {
        val theirs = (other as? Secret)?.value ?: return false
        return theirs.length == value.length && indistinguishableFrom(theirs)
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = REDACTED

    private fun indistinguishableFrom(theirs: String): Boolean {
        var difference = 0
        for (index in value.indices) {
            difference = difference or (value[index].code xor theirs[index].code)
        }
        return difference == 0
    }

    private companion object {
        /**
         * Not an empty string: a redacted field has to be visibly a field, or a reader cannot
         * tell "the password is hidden" from "the password is missing".
         */
        const val REDACTED = "***"
    }
}
