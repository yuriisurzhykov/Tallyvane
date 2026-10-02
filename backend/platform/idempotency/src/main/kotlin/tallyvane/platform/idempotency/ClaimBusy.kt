package tallyvane.platform.idempotency

/**
 * Another request holds this key and has not finished, and did not within the wait the database allows.
 *
 * The edge answers 409 with `Retry-After`: the same request, sent again, will be answered by what the
 * first one leaves behind. Nothing of the waiting request's work has happened.
 */
public class ClaimBusy :
    RuntimeException(
        "Another request with this Idempotency-Key is still running, and did not finish within the lock wait. " +
            "Send the same request again.",
    )
