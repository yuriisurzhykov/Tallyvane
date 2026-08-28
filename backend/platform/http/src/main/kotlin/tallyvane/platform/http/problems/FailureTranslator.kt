package tallyvane.platform.http.problems

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.status.Answers

/**
 * Turns a failure that escaped as an exception into a [Problem].
 *
 * [Problems] covers what a use case *reported*. This covers what nobody reported: a driver refusing
 * on a serialization failure, a pool with no connections left, a lock timeout, a genuine bug. None
 * of those travel through an outcome, so no `when` in a route can see them — which makes a central
 * place not a convenience but the only way they get an answer instead of leaking one.
 *
 * Chain of responsibility, and the shape follows from who knows what: a platform module knows its
 * own technical failures, a capability knows its own, and `platform:http` may know neither. Each
 * link answers for what it recognises and passes the rest along.
 *
 * [translate] takes an [Answers] receiver for the same reason [Problems.of] does: it is the only
 * source of a [Problem], and a translator is the other place allowed to hold one.
 */
public interface FailureTranslator {
    /**
     * @return the answer for [failure], or `null` if this link does not recognise it.
     */
    public fun Answers.translate(failure: Throwable): Problem?

    /**
     * Asks each link in order and takes the first answer.
     *
     * Order is meaningful and belongs to `app`: a link that recognises broadly comes after the ones
     * that recognise precisely, and [Unrecognised] comes last because it recognises everything.
     */
    public class Chained(private val links: List<FailureTranslator>) : FailureTranslator {
        override fun Answers.translate(failure: Throwable): Problem? =
            links.firstNotNullOfOrNull { link -> with(link) { translate(failure) } }
    }

    /**
     * Recognises everything and says nothing: [Answers.unexpected], which has no field a message
     * could reach.
     *
     * If this link answers, there is a bug — a failure nobody translated. It belongs at the end of
     * every chain so that "nobody translated it" produces a correct 500 rather than a leak.
     *
     * A class rather than an `object` because `no-stateful-objects` forbids an object with
     * functions, and it is right to: a hidden singleton is a dependency nobody declared. This one
     * holds nothing, so constructing it costs nothing.
     */
    public class Unrecognised : FailureTranslator {
        override fun Answers.translate(failure: Throwable): Problem = unexpected()
    }
}
