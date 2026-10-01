package tallyvane.authentication.application.port

import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.domain.Attempt
import kotlin.uuid.Uuid

/**
 * Where sign-in attempts are kept between the requests one sign-in is spread over (ADR-078).
 *
 * An attempt cannot live in the memory of a server that may restart, or with a client that may
 * throw it away, so it is kept here: the client holds only the [Uuid] it is kept under.
 *
 * Both methods run inside the caller's transaction (`TransactionRunner`, ADR-052) and block on the
 * database. `suspend` would only say the caller may be a coroutine, not that no thread waits
 * (ADR-058), so these do not pretend to.
 *
 * ### Two requests at once cannot lose a wrong answer
 *
 * An attempt only grows, and two requests may load the same one and each add something: two guesses
 * of a code sent together. If the second save simply replaced the first, one wrong answer would
 * vanish and the failure limit would be a number a client can beat by sending requests in parallel.
 * So [save] keeps an attempt only when it contains everything already kept, and answers
 * [AttemptSaveOutcome.Superseded] otherwise; the caller loads again and applies its change to what
 * is there.
 *
 * ### A wrong answer must survive a refusal
 *
 * A caller that records a wrong answer and then refuses the request must not do both in one
 * transaction that rolls back: the answer would be forgotten along with the refusal (see
 * `Verdict`). Recording it belongs in a transaction of its own that commits.
 */
public interface Attempts {
    /**
     * The attempt kept under [id], or null if none is, or none is any longer.
     *
     * @throws IllegalStateException if what is kept could not have been written by [Attempt.writeTo].
     */
    public fun find(id: Uuid): Attempt?

    /**
     * Keeps [attempt] under [id]: as a new attempt the first time, and on later calls as the same
     * attempt with whatever it gained since.
     *
     * Saving what is already kept again changes nothing and still answers [AttemptSaveOutcome.Saved].
     * Instants are kept to the microsecond, as the database keeps them, so an attempt saved twice
     * without being loaded in between is not mistaken for two different histories.
     */
    public fun save(id: Uuid, attempt: Attempt): AttemptSaveOutcome
}
