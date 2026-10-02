package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure

/**
 * How finishing the welcome form ended.
 */
public sealed interface RegisterOutcome {
    /**
     * The account exists. The sign-in is now complete and waits to be redeemed for a session, which
     * is `sessions`' step (ADR-076): registering grants no access by itself.
     */
    public class Registered : RegisterOutcome {
        override fun equals(other: Any?): Boolean = other is Registered

        override fun hashCode(): Int = Registered::class.hashCode()

        override fun toString(): String = "Registered"
    }

    public sealed interface Failed :
        RegisterOutcome,
        Failure {
        /**
         * There is no registration to finish: no cookie, one that expired, or one already used.
         */
        public class NoRegistration : Failed {
            override fun equals(other: Any?): Boolean = other is NoRegistration

            override fun hashCode(): Int = NoRegistration::class.hashCode()

            override fun toString(): String = "NoRegistration"
        }

        /**
         * The person did not agree to the privacy policy. Nothing was created.
         */
        public class ConsentMissing : Failed {
            override fun equals(other: Any?): Boolean = other is ConsentMissing

            override fun hashCode(): Int = ConsentMissing::class.hashCode()

            override fun toString(): String = "ConsentMissing"
        }

        /**
         * The chosen name is not one `identity` accepts. Nothing was created.
         */
        public class NameRefused : Failed {
            override fun equals(other: Any?): Boolean = other is NameRefused

            override fun hashCode(): Int = NameRefused::class.hashCode()

            override fun toString(): String = "NameRefused"
        }
    }
}
