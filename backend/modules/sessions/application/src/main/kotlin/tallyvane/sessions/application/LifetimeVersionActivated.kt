package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure

/**
 * How putting a kept version of the lifetimes in force ended (ADR-098).
 */
public sealed interface LifetimeVersionActivated {
    /**
     * The version is the one in force, from this moment, and the activation is kept with who made it and why. The
     * next request of every session of the kind of client is judged by it.
     */
    public class Activated internal constructor() : LifetimeVersionActivated {
        override fun equals(other: Any?): Boolean = other is Activated

        override fun hashCode(): Int = Activated::class.hashCode()

        override fun toString(): String = "Activated"
    }

    public sealed interface Failed :
        LifetimeVersionActivated,
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
         * The request names a kind of client that does not exist, or gives no comment: every fault, never none.
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
