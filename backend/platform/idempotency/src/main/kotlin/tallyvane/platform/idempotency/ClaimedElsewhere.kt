package tallyvane.platform.idempotency

/**
 * Another request already did this one's work and committed it.
 *
 * An exception because it has to travel through code that does not know it exists. The use case that
 * was running has to be unwound with its transaction rolled back, and it was written without a line
 * about idempotency, so there is no outcome of its own to carry the news. The edge catches it and
 * answers the repeat from what the first request left in the [Ledger].
 */
public class ClaimedElsewhere :
    RuntimeException(
        "Another request holds this Idempotency-Key and has committed. " +
            "The edge answers the repeat from what that request stored; nothing here has run.",
    )
