package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.Secret

/**
 * How issuing new recovery codes ended.
 */
public sealed interface CodesRegenerated {
    /**
     * Ten new codes replace the old set. They are told here once, and kept only as digests.
     */
    public class Regenerated internal constructor(private val codes: List<Secret>) : CodesRegenerated {
        /**
         * Tells [shown] the recovery codes, in the form a person writes down.
         */
        public fun writeTo(shown: (List<Secret>) -> Unit) {
            shown(codes.toList())
        }

        override fun toString(): String = "Regenerated(***)"
    }

    public sealed interface Failed :
        CodesRegenerated,
        Failure {
        /**
         * TOTP is not on, so there is no set to replace. A retired seed counts as off here: the person turns
         * TOTP on again, which issues a set of its own.
         */
        public class NotActive internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NotActive

            override fun hashCode(): Int = NotActive::class.hashCode()

            override fun toString(): String = "NotActive"
        }
    }
}
