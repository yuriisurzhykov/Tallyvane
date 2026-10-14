package tallyvane.sessions.application

/**
 * What putting a version in force would do to the sessions that exist (ADR-098).
 *
 * @param sessions How many sessions of the kind of client are live now.
 * @param wouldEnd How many of them the lifetimes would end at the next request. Never more than [sessions].
 */
public class LifetimeConsequences(private val sessions: Int, private val wouldEnd: Int) {
    init {
        require(sessions >= 0 && wouldEnd in 0..sessions) {
            "$wouldEnd sessions would end out of $sessions: the sessions that would end are part of the live ones."
        }
    }

    /**
     * Tells [record] how many sessions are live and how many would end.
     */
    public fun writeTo(record: Record) {
        record.consequences(sessions, wouldEnd)
    }

    override fun equals(other: Any?): Boolean =
        other is LifetimeConsequences && other.sessions == sessions && other.wouldEnd == wouldEnd

    override fun hashCode(): Int = 31 * sessions + wouldEnd

    override fun toString(): String = "LifetimeConsequences(sessions=$sessions, wouldEnd=$wouldEnd)"

    /**
     * What the consequences tell whoever shows them.
     */
    public fun interface Record {
        public fun consequences(sessions: Int, wouldEnd: Int)
    }
}
