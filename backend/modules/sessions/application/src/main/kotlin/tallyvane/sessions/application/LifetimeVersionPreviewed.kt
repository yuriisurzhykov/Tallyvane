package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure

/**
 * How asking what a kept version of the lifetimes would do ended (ADR-098).
 */
public sealed interface LifetimeVersionPreviewed {
    /**
     * What putting the version in force would do to the sessions that exist.
     */
    public class Previewed internal constructor(private val consequences: LifetimeConsequences) :
        LifetimeVersionPreviewed {
        /**
         * Tells [record] how many sessions are live and how many would end.
         */
        public fun writeTo(record: LifetimeConsequences.Record) {
            consequences.writeTo(record)
        }

        override fun toString(): String = "Previewed($consequences)"
    }

    public sealed interface Failed :
        LifetimeVersionPreviewed,
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

        /**
         * No version with that number is kept for the kind of client.
         */
        public class NoSuchVersion internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NoSuchVersion

            override fun hashCode(): Int = NoSuchVersion::class.hashCode()

            override fun toString(): String = "NoSuchVersion"
        }
    }
}
