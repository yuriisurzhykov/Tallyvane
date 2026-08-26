package konsist.fixtures.failurehasproblems

interface Failure

// A grouped failure root with no Problems implementation anywhere: a route can return it and
// nothing turns it into an answer.
sealed interface SaveJobFailed : Failure {
    data class RangeInvalid(val min: Int, val max: Int) : SaveJobFailed

    data object NotYours : SaveJobFailed
}
