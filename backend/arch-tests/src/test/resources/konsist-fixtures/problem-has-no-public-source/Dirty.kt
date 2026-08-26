package konsist.fixtures.problemhasnopublicsource

// A public constructor and a convenience factory: with either one, a route can answer with a
// problem it built itself and never consult its module's mapping table. That is the hole ADR-062
// closed, and this is what reopening it looks like.
class Problem(
    val type: String,
    val status: Int,
) {
    companion object {
        fun forbidden(): Problem = Problem("forbidden", 403)
    }
}
