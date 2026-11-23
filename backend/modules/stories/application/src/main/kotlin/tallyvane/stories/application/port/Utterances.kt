package tallyvane.stories.application.port

import tallyvane.platform.kernel.UntrustedText
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The person's own words, kept as they were said and never changed (law 1: words are immutable).
 */
public interface Utterances {
    public fun keep(id: Uuid, account: Uuid, story: Uuid, words: UntrustedText, at: Instant)

    /**
     * How many utterances [account] has told.
     */
    public fun countOf(account: Uuid): Int
}
