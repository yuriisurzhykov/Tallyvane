package tallyvane.authentication.application

import tallyvane.platform.kernel.Secret

/**
 * A sign-in that has begun: the secret the browser keeps for it, and the Google address to send the
 * person to.
 */
public class SignInBegun internal constructor(private val attempt: Secret, private val address: String) {
    /**
     * Tells [record] the secret and the address.
     */
    public fun writeTo(record: Record) {
        record.begun(attempt, address)
    }

    override fun toString(): String = "SignInBegun(***)"

    /**
     * Whoever hands the two to the browser.
     */
    public fun interface Record {
        public fun begun(attempt: Secret, address: String)
    }
}
