package tallyvane.authentication.application.port

import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.domain.Attempt
import tallyvane.platform.kernel.Digest

/**
 * Where sign-in attempts are kept between the requests one sign-in is spread over (ADR-078).
 *
 * An attempt cannot live in the memory of a server that may restart, or with a client that may
 * throw it away, so it is kept here. The client holds a secret, and the attempt is kept under the
 * [Digest] of that secret: a random value the browser carries in a cookie, of which the database holds
 * only the keyed hash (slice 3, fork 2). Whoever reads the table cannot continue anyone's sign-in, and
 * nobody can guess one, which a UUIDv7 would have let them come close to.
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
 * is there, with the time read from the clock again: an attempt refuses a time earlier than one it
 * already holds, so the time of the request that lost the race cannot be reused.
 *
 * ### A wrong answer must survive a refusal
 *
 * A caller that records a wrong answer and then refuses the request must not do both in one
 * transaction that rolls back: the answer would be forgotten along with the refusal (see
 * `Verdict`). Recording it belongs in a transaction of its own that commits.
 */
public interface Attempts {
    /**
     * The attempt kept under [key], or null if none is, or none is any longer.
     *
     * @throws IllegalStateException if what is kept could not have been written by [Attempt.writeTo].
     */
    public fun find(key: Digest): Attempt?

    /**
     * Keeps [attempt] under [key]: as a new attempt the first time, and on later calls as the same
     * attempt with whatever it gained since.
     *
     * Saving what is already kept again changes nothing and still answers [AttemptSaveOutcome.Saved].
     * Instants are kept to the microsecond, as the database keeps them, so an attempt saved twice
     * without being loaded in between is not mistaken for two different histories.
     */
    public fun save(key: Digest, attempt: Attempt): AttemptSaveOutcome

    /**
     * Forgets the attempt kept under [key], with everything kept beside it, such as its Google
     * handshake. Forgetting one that is not kept changes nothing.
     *
     * @return whether this call is the one that removed it. Two requests that both read an attempt and then
     * both forget it cannot both be told true, so the one that must be single-use takes it only when it is.
     */
    public fun forget(key: Digest): Boolean
}
