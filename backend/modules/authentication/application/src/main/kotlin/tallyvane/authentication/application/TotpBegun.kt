package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.Secret

/**
 * How beginning to enable TOTP ended.
 */
public sealed interface TotpBegun {
    /**
     * A pending enrolment exists, and this is the only time its key is told.
     */
    public class Started internal constructor(private val key: Secret, private val uri: Secret) : TotpBegun {
        /**
         * Tells [shown] the key to put in an authenticator app and the `otpauth://` address that carries it.
         */
        public fun writeTo(shown: (key: Secret, uri: Secret) -> Unit) {
            shown(key, uri)
        }

        override fun toString(): String = "Started(***)"
    }

    public sealed interface Failed :
        TotpBegun,
        Failure {
        /**
         * TOTP is already on. Beginning again would replace a working seed; the person turns it off first.
         */
        public class AlreadyActive internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is AlreadyActive

            override fun hashCode(): Int = AlreadyActive::class.hashCode()

            override fun toString(): String = "AlreadyActive"
        }
    }
}
