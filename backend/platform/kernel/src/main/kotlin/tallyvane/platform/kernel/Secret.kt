package tallyvane.platform.kernel

/**
 * A value that must not appear in a log line, an exception message or a crash dump.
 *
 * ### The defect this closes
 *
 * `DatabaseAccess` was a `data class` holding `password: String`, so its generated `toString()`
 * printed the password along with everything else. Nothing had leaked yet, but nothing prevented
 * it either: one interpolation of the whole object into a log line, or one exception message that
 * included it, and a database password would have been written to disk. §17 forbids leaking
 * internal detail into anything a reader can see, and a type is a better guard than a rule
 * nobody can enforce.
 *
 * Two secrets exist as of 2026-08-26 — the database password and the health service token — which
 * is why this is a shared type rather than a `toString()` override on one class.
 *
 * ### Why comparison is constant-time
 *
 * `==` on strings returns as soon as two characters differ, so the time it takes reveals how much
 * of a guess was right. Given enough attempts that recovers a secret one character at a time. This
 * comparison looks at every character of both values whatever it finds.
 *
 * The length is compared first, and that does leak the length. Hiding it would mean hashing both
 * sides on every comparison; a length is not the secret, and paying a hash on every request to
 * conceal it would be the wrong trade. [tallyvane.platform.health.ServiceToken] makes the same
 * call for the same reason.
 *
 * ### What this type does not do
 *
 * It does not validate. An empty secret is a configuration question, not a property of the type:
 * `ServiceToken` treats an empty expected value as a closed door on purpose, and a length floor
 * for a specific setting belongs to whatever reads that setting.
 */
public class Secret(private val value: String) {
    /**
     * The value, for the one place that has to hand it to a driver or compare it.
     *
     * Named to be conspicuous at the call site: `access.password.revealed()` reads as a decision,
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
