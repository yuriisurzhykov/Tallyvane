package tallyvane.identity.application

import tallyvane.platform.kernel.Failure

/**
 * How asking who an account is ended.
 */
public sealed interface WhoAmIOutcome {
    /**
     * The account exists; this is who it is.
     */
    public class Known internal constructor(private val profile: Profile) : WhoAmIOutcome {
        /**
         * Tells [record] who this is.
         */
        public fun writeTo(record: Profile.Record) {
            profile.writeTo(record)
        }

        override fun toString(): String = "Known"
    }

    public sealed interface Failed :
        WhoAmIOutcome,
        Failure {
        /**
         * Nobody is kept under the account a session named: it was deleted while the session lived.
         */
        public class Gone : Failed {
            override fun equals(other: Any?): Boolean = other is Gone

            override fun hashCode(): Int = Gone::class.hashCode()

            override fun toString(): String = "Gone"
        }
    }
}
