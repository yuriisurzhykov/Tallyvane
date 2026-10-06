package tallyvane.platform.http

import tallyvane.platform.idempotency.Owner
import kotlin.uuid.Uuid

/**
 * Who a request comes from, as authentication concluded before any route ran.
 *
 * Closed on purpose, like the answers it leads to: a request is from a person, from nobody who
 * presented a credential, or from someone whose credential no longer works. The first reaches a
 * route; the other two are told apart only because the client must react differently (sign in, or
 * sign in again over the page it is on, ADR-084). The account is private to [Signed], and the one way
 * to read it is [reportTo], so a route that wants it must say what it does when there is none. A signed-in
 * caller also carries the reference of the session the request came from, which the modules that tell the
 * security journal what a person did hand on without looking inside it (ADR-095).
 */
public sealed interface Caller {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * Whose `Idempotency-Key` a request's is: the person, and nobody otherwise (ADR-086).
     */
    public fun owner(): Owner

    /**
     * What a [Caller] says about itself, one method per case.
     */
    public interface Report<out T> {
        public fun signedIn(account: Uuid, session: Uuid): T

        public fun confirmed(account: Uuid, session: Uuid): T

        public fun lapsed(): T

        public fun anonymous(): T
    }

    /**
     * A person the request proved to be, who has not proved it again recently: enough for everything but
     * the acts behind [Access.SignedFresh].
     */
    public class Signed(private val account: Uuid, private val session: Uuid) : Caller {
        override fun <T> reportTo(report: Report<T>): T = report.signedIn(account, session)

        override fun owner(): Owner = Owner.subject(account)

        override fun equals(other: Any?): Boolean =
            other is Signed && other.account == account && other.session == session

        override fun hashCode(): Int = 31 * account.hashCode() + session.hashCode()

        override fun toString(): String = "Signed"
    }

    /**
     * A person the request proved to be, who also proved it again recently enough for a dangerous act
     * (ADR-092). What "recently enough" means is the issuer of credentials' to say; the edge only asks.
     */
    public class Confirmed(private val account: Uuid, private val session: Uuid) : Caller {
        override fun <T> reportTo(report: Report<T>): T = report.confirmed(account, session)

        override fun owner(): Owner = Owner.subject(account)

        override fun equals(other: Any?): Boolean =
            other is Confirmed && other.account == account && other.session == session

        override fun hashCode(): Int = 31 * account.hashCode() + session.hashCode() + 1

        override fun toString(): String = "Confirmed"
    }

    /**
     * The request carried a credential that is no longer good: a session that ended, or one nobody
     * issued.
     */
    public class Lapsed : Caller {
        override fun <T> reportTo(report: Report<T>): T = report.lapsed()

        override fun owner(): Owner = Owner.anonymous()

        override fun equals(other: Any?): Boolean = other is Lapsed

        override fun hashCode(): Int = Lapsed::class.hashCode()

        override fun toString(): String = "Lapsed"
    }

    /**
     * The request carried no credential at all.
     */
    public class Anonymous : Caller {
        override fun <T> reportTo(report: Report<T>): T = report.anonymous()

        override fun owner(): Owner = Owner.anonymous()

        override fun equals(other: Any?): Boolean = other is Anonymous

        override fun hashCode(): Int = Anonymous::class.hashCode()

        override fun toString(): String = "Anonymous"
    }
}
