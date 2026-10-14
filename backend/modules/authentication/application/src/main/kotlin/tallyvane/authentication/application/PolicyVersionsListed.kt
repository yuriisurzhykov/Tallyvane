package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure

/**
 * How asking for the versions of a purpose's policy ended (ADR-098).
 */
public sealed interface PolicyVersionsListed {
    /**
     * The versions kept, newest first.
     */
    public class Listed internal constructor(private val versions: KeptVersions) : PolicyVersionsListed {
        /**
         * Tells [record] about each version in turn.
         */
        public fun writeTo(record: KeptVersions.Record) {
            versions.writeTo(record)
        }

        override fun toString(): String = "Listed($versions)"
    }

    public sealed interface Failed :
        PolicyVersionsListed,
        Failure {
        /**
         * The account asking does not hold the right to administer, or no longer does.
         */
        public class NotAnAdministrator internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NotAnAdministrator

            override fun hashCode(): Int = NotAnAdministrator::class.hashCode()

            override fun toString(): String = "NotAnAdministrator"
        }

        /**
         * The request names a purpose that does not exist.
         */
        public class Invalid internal constructor(private val faults: List<Fault>) : Failed {
            /**
             * Tells [record] each fault.
             */
            public fun writeTo(record: Fault.Record) {
                faults.forEach { it.writeTo(record) }
            }

            override fun equals(other: Any?): Boolean = other is Invalid && other.faults == faults

            override fun hashCode(): Int = faults.hashCode()

            override fun toString(): String = "Invalid($faults)"
        }
    }
}
