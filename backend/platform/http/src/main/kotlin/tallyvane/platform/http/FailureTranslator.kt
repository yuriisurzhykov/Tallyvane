package tallyvane.platform.http

/**
 * Turns a failure that escaped as an exception into a [Problem].
 *
 * [Problems] covers what a use case *reported*. This covers what nobody reported: a driver
 * throwing on a serialization failure, a pool that ran out of connections, a lock timeout, a
 * genuine bug. None of those pass through a use case's outcome, so no `when` in a route can see
 * them — which is why a central place is not a convenience here but the only way they get an
 * answer at all instead of leaking one.
 *
 * Chain of responsibility, and the shape follows from who knows what: a platform module knows
 * its own technical failures, a capability knows its own, and `platform:http` may know neither.
 * Each link answers for what it recognises and passes on the rest.
 *
 * @see Chained for composition, [Unrecognised] for the tail every chain ends with.
 */
public interface FailureTranslator {
    /**
     * @return the answer for [failure], or `null` if this link does not recognise it.
     */
    public fun translate(failure: Throwable): Problem?

    /**
     * Asks each link in order and takes the first answer.
     *
     * Order is meaningful and belongs to `app`: a link that recognises broadly must come after
     * the ones that recognise precisely, and [Unrecognised] must come last because it
     * recognises everything.
     */
    public class Chained(private val links: List<FailureTranslator>) : FailureTranslator {
        override fun translate(failure: Throwable): Problem? =
            links.firstNotNullOfOrNull { link -> link.translate(failure) }
    }

    /**
     * Recognises everything and says nothing: [Problem.unexpected], which has no field a
     * message could reach.
     *
     * If this link answers, there is a bug — a failure nobody translated. It belongs at the end
     * of every chain so that "nobody translated it" produces a correct 500 rather than a leak.
     *
     * A class rather than an `object` because `no-stateful-objects` forbids an object with
     * functions, and it is right to: a hidden singleton is a dependency nobody declared. This one
     * holds nothing, so constructing it costs nothing either.
     */
    public class Unrecognised : FailureTranslator {
        override fun translate(failure: Throwable): Problem = Problem.unexpected()
    }
}
