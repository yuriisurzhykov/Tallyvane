package tallyvane.journal.application

import tallyvane.journal.domain.Entry
import tallyvane.platform.kernel.Failure

/**
 * How asking for a page of the journal ended.
 */
public sealed interface ActivityOutcome {
    /**
     * The page. Tells it through [writeTo] and nothing else.
     */
    public class Shown internal constructor(private val page: ActivityPage) : ActivityOutcome {
        /**
         * Tells [record] each entry in turn, with its device just after it, then the cursor of the next page.
         */
        public fun writeTo(record: Record) {
            page.writeTo(
                object :
                    ActivityPage.Record,
                    Entry.Record by record {
                    override fun next(cursor: Long?) {
                        record.next(cursor?.toString())
                    }
                },
            )
        }

        override fun toString(): String = "Shown($page)"

        /**
         * Whoever shows the page, told each entry and then the cursor of the next page, or null on the last. A
         * cursor is a word to hand back and nothing to take apart.
         */
        public interface Record : Entry.Record {
            public fun next(cursor: String?)
        }
    }

    public sealed interface Failed :
        ActivityOutcome,
        Failure {
        /**
         * The cursor is not a positive whole number.
         */
        public class UnknownCursor internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is UnknownCursor

            override fun hashCode(): Int = UnknownCursor::class.hashCode()

            override fun toString(): String = "UnknownCursor"
        }
    }
}
