package tallyvane.sessions.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Where a session stands at a moment: still good, or over, and why.
 *
 * Closed on purpose, like `Progress` in `authentication`: the contents are private and the only way to
 * read them is [reportTo], one method per case, so a reader cannot forget that a session can end.
 */
public sealed interface Standing {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * The session may be used.
     */
    @ConsistentCopyVisibility
    public data class Live internal constructor(
        private val session: SessionId,
        private val account: Uuid,
        private val factors: Set<Factor>,
        private val authenticatedAt: Instant,
    ) : Standing {
        override fun <T> reportTo(report: Report<T>): T = report.live(session, account, factors, authenticatedAt)

        override fun toString(): String = "Live(authenticatedAt=$authenticatedAt)"
    }

    /**
     * It went unused for longer than it may.
     */
    public class EndedByIdleness internal constructor() : Standing {
        override fun <T> reportTo(report: Report<T>): T = report.endedByIdleness()

        override fun equals(other: Any?): Boolean = other is EndedByIdleness

        override fun hashCode(): Int = EndedByIdleness::class.hashCode()

        override fun toString(): String = "EndedByIdleness"
    }

    /**
     * It lived as long as it may, however much it was used.
     */
    public class EndedByAge internal constructor() : Standing {
        override fun <T> reportTo(report: Report<T>): T = report.endedByAge()

        override fun equals(other: Any?): Boolean = other is EndedByAge

        override fun hashCode(): Int = EndedByAge::class.hashCode()

        override fun toString(): String = "EndedByAge"
    }

    /**
     * A reader of [Standing], one method per case.
     */
    public interface Report<out T> {
        /**
         * @param session Which session it is, to point at it later.
         * @param account Whose session it is; the domain does not know what an account is.
         * @param factors How they proved who they are.
         * @param authenticatedAt When they did.
         */
        public fun live(session: SessionId, account: Uuid, factors: Set<Factor>, authenticatedAt: Instant): T

        public fun endedByIdleness(): T

        public fun endedByAge(): T
    }
}
