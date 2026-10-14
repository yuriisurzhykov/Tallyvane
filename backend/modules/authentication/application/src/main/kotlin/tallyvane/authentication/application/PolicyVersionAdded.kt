package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure

/**
 * How adding a version of a purpose's policy ended (ADR-098).
 */
public sealed interface PolicyVersionAdded {
    /**
     * The version is kept, numbered [number] among the versions of its purpose. It is not in force: that is a
     * separate act.
     */
    public class Added internal constructor(private val purpose: String, private val number: Int) :
        PolicyVersionAdded {
        /**
         * Tells [record] the word of the purpose and the number the version was given.
         */
        public fun writeTo(record: Record) {
            record.added(purpose, number)
        }

        override fun equals(other: Any?): Boolean = other is Added && other.purpose == purpose && other.number == number

        override fun hashCode(): Int = 31 * purpose.hashCode() + number

        override fun toString(): String = "Added(purpose=$purpose, number=$number)"

        /**
         * What an added version tells whoever shows it.
         */
        public fun interface Record {
            public fun added(purpose: String, number: Int)
        }
    }

    public sealed interface Failed :
        PolicyVersionAdded,
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
         * What was sent is not a policy that may exist, or has no comment: every fault found, never none.
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
