package tallyvane.stories.application

import tallyvane.platform.kernel.UntrustedText
import tallyvane.stories.application.port.Utterances
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [Utterances] in memory.
 */
class UtterancesFake : Utterances {
    private val kept = mutableListOf<Pair<Uuid, Uuid>>()

    override fun keep(id: Uuid, account: Uuid, story: Uuid, words: UntrustedText, at: Instant) {
        require(kept.none { it.first == id }) { "utterance $id is kept already" }
        kept += id to account
    }

    override fun countOf(account: Uuid): Int = kept.count { it.second == account }

    override fun toString(): String = "UtterancesFake(${kept.size})"
}
