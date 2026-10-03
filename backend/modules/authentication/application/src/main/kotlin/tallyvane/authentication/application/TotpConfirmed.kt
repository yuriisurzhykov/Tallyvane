package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.Secret

/**
 * How typing the first code ended.
 */
public sealed interface TotpConfirmed {
    /**
     * TOTP is on. The ten recovery codes are told here once, and kept only as digests.
     */
    public class Confirmed internal constructor(private val codes: List<Secret>) : TotpConfirmed {
        /**
         * Tells [shown] the recovery codes, in the form a person writes down.
         */
        public fun writeTo(shown: (List<Secret>) -> Unit) {
            shown(codes.toList())
        }

        override fun toString(): String = "Confirmed(***)"
    }

    public sealed interface Failed :
        TotpConfirmed,
        Failure {
        /**
         * Nothing was begun, or it is already on, so there is no first code to type.
         */
        public class NotBegun internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NotBegun

            override fun hashCode(): Int = NotBegun::class.hashCode()

            override fun toString(): String = "NotBegun"
        }

        /**
         * The code was not right. The enrolment stays pending and the person may type another.
         */
        public class WrongCode internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is WrongCode

            override fun hashCode(): Int = WrongCode::class.hashCode()

            override fun toString(): String = "WrongCode"
        }
    }
}
