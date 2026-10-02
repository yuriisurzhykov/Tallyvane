package tallyvane.platform.http

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each refusal of the edge means in HTTP (ADR-086), within the closed set of meanings of ADR-062.
 *
 * Two of them are a 409, since that set has one conflict. They are told apart by `Retry-After`, which
 * [InProgress] carries and [Lost] does not: present means "send the same request again", absent means
 * "do not, read the current state instead".
 */
internal class IdempotencyProblems : Problems<IdempotencyFailure> {
    override fun Answers.of(failure: IdempotencyFailure): Problem = when (failure) {
        IdempotencyFailure.Missing -> malformed(
            "$UNSAFE_METHODS need an Idempotency-Key header: a UUID you generate once for one " +
                "intention and send again, unchanged, if you repeat it.",
        )

        IdempotencyFailure.Malformed -> malformed("The Idempotency-Key header must be a UUID.")

        IdempotencyFailure.TooLarge -> malformed(
            "The request body is larger than $BODY_LIMIT_TEXT, which is the most the edge reads to check " +
                "a repeated request.",
        )

        IdempotencyFailure.Reused -> invalid(
            listOf(FieldError(IDEMPOTENCY_KEY_HEADER, "idempotency-key.reused")),
            "This Idempotency-Key was already used for a different request. Generate a new key for each " +
                "new request, and repeat a key only for the same request.",
        )

        IdempotencyFailure.InProgress -> conflicting(
            "A request with this Idempotency-Key is still being processed. Send the same request again.",
        )

        IdempotencyFailure.Lost -> conflicting(
            "A request with this Idempotency-Key was already carried out, and its answer cannot be given " +
                "again. Read the current state instead of repeating the request.",
        )
    }
}
