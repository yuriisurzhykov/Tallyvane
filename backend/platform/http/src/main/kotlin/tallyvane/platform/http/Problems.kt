package tallyvane.platform.http

import tallyvane.platform.kernel.Failure

/**
 * One module's table of "this failure means that HTTP answer".
 *
 * ### What makes it unavoidable rather than merely available
 *
 * [of] runs with an [Answers] receiver, and that receiver is the only source of a [Problem]
 * anywhere. The renderer supplies it; nobody else has one. So a module cannot produce an error
 * answer outside this method — and since the only way a route can *answer* with a failure is
 * [Refused], which cannot be constructed without a `Problems<F>` of the matching `F`, the route
 * must hold one, must ask for it in its constructor, and `app` must build it. Every link is the
 * compiler's, not a reviewer's.
 *
 * An earlier version had public factories on `Problem` and relied on `failure-has-problems` to
 * check that a table existed. It checked existence and nothing else: a route could build a problem
 * by hand and never call the table. That gap is what this receiver closes.
 *
 * ### One table per failure root
 *
 * [of] takes the whole sealed branch, so its `when` is exhaustive with no `else`, and adding a
 * case breaks compilation until it is mapped. Contravariant in [F] so one table serves every
 * member of the branch.
 *
 * ```kotlin
 * internal class JobProblems : Problems<SaveJobOutcome.Failed> {
 *     override fun Answers.of(failure: SaveJobOutcome.Failed): Problem = when (failure) {
 *         is RangeInvalid -> invalid(listOf(FieldError("salary_min_cents", "range.invalid")))
 *         is NotYours     -> forbidden()
 *     }
 * }
 * ```
 */
public interface Problems<in F : Failure> {
    public fun Answers.of(failure: F): Problem
}
