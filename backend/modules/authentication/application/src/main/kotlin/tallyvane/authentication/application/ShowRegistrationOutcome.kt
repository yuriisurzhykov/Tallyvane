package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure

/**
 * What the welcome screen can show.
 */
public sealed interface ShowRegistrationOutcome {
    /**
     * A registration to finish; [writeTo] tells what Google said, to prefill the form.
     */
    public class Welcome internal constructor(private val profile: GoogleProfile) : ShowRegistrationOutcome {
        public fun writeTo(record: GoogleProfile.Record) {
            profile.writeTo(record)
        }

        override fun toString(): String = "Welcome(***)"
    }

    public sealed interface Failed :
        ShowRegistrationOutcome,
        Failure {
        /**
         * There is no registration to finish: no cookie, one that expired, or one already used.
         */
        public class NoRegistration : Failed {
            override fun equals(other: Any?): Boolean = other is NoRegistration

            override fun hashCode(): Int = NoRegistration::class.hashCode()

            override fun toString(): String = "NoRegistration"
        }
    }
}
