package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure

/**
 * How adding a version of a kind of client's lifetimes ended (ADR-098).
 */
public sealed interface LifetimeVersionAdded {
    /**
     * The version is kept, numbered [number] among the versions of its kind of client. It is not in force: that
     * is a separate act.
     */
    public class Added internal constructor(private val client: String, private val number: Int) :
        LifetimeVersionAdded {
        /**
         * Tells [record] the word of the kind of client and the number the version was given.
         */
        public fun writeTo(record: Record) {
            record.added(client, number)
        }

        override fun equals(other: Any?): Boolean = other is Added && other.client == client && other.number == number

        override fun hashCode(): Int = 31 * client.hashCode() + number

        override fun toString(): String = "Added(client=$client, number=$number)"

        /**
         * What an added version tells whoever shows it.
         */
        public fun interface Record {
            public fun added(client: String, number: Int)
        }
    }

    public sealed interface Failed :
        LifetimeVersionAdded,
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
         * What was sent are not lifetimes that may exist, or has no comment: every fault found, never none.
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
