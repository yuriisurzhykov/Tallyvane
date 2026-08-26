package tallyvane.platform.http

import tallyvane.platform.kernel.Failure

/**
 * One module's table of "this failure means that HTTP answer".
 *
 * The type parameter is what makes the arrangement a contract rather than a habit. A route
 * receiving a sealed outcome must handle its failure branch — the `when` does not compile
 * otherwise — and the only way to answer a failure is a [Problem], which only this port
 * produces for module failures. So the route must hold a `Problems<F>`, which means its
 * constructor must ask for one, which means `app` must build one. Forget any link and the
 * build stops; there is no path that ends in a forgotten mapping.
 *
 * Contravariant in [F] so `Problems<Failed>` serves every member of that branch.
 *
 * One implementation per failure root, not per case: [of] takes the whole branch, so its `when`
 * is exhaustive with no `else`, and adding a case breaks compilation until it is mapped. An
 * earlier sketch had the mapper take `Throwable` and return null for "not mine", which needed
 * an `else` — and an `else` is exactly how a new failure silently becomes a 500.
 *
 * ```kotlin
 * internal class JobProblems : Problems<SaveJobOutcome.Failed> {
 *     override fun of(failure: SaveJobOutcome.Failed): Problem = when (failure) {
 *         is RangeInvalid -> Problem.invalid(FieldError("salary_min_cents", "range.invalid"))
 *         is NotYours     -> Problem.forbidden()
 *     }
 * }
 * ```
 */
public interface Problems<in F : Failure> {
    public fun of(failure: F): Problem
}
