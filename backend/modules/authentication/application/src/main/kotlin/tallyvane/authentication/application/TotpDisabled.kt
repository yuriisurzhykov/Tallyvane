package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure

/**
 * How turning TOTP off ended.
 */
public sealed interface TotpDisabled {
    /**
     * The enrolment and every recovery code are gone. The account signs in with Google alone again.
     */
    public class Disabled internal constructor() : TotpDisabled {
        override fun equals(other: Any?): Boolean = other is Disabled

        override fun hashCode(): Int = Disabled::class.hashCode()

        override fun toString(): String = "Disabled"
    }

    public sealed interface Failed :
        TotpDisabled,
        Failure {
        /**
         * There is nothing to turn off.
         */
        public class NotEnabled internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NotEnabled

            override fun hashCode(): Int = NotEnabled::class.hashCode()

            override fun toString(): String = "NotEnabled"
        }
    }
}
