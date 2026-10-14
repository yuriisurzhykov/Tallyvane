package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure

/**
 * How asking for the versions of a kind of client's lifetimes ended (ADR-098).
 */
public sealed interface LifetimeVersionsListed {
    /**
     * The versions kept, newest first.
     */
    public class Listed internal constructor(private val versions: KeptLifetimes) : LifetimeVersionsListed {
        /**
         * Tells [record] about each version in turn.
         */
        public fun writeTo(record: KeptLifetimes.Record) {
            versions.writeTo(record)
        }

        override fun toString(): String = "Listed($versions)"
    }

    public sealed interface Failed :
        LifetimeVersionsListed,
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
         * The request names a kind of client that does not exist.
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
