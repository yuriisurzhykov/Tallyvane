package tallyvane.authentication.application

import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.domain.Attempt
import kotlin.uuid.Uuid

/**
 * [Attempts] that keeps them in a map, for tests of the code that uses the port (ADR-044).
 *
 * Judges "does this attempt contain what is kept?" exactly as the adapter over Postgres does, by
 * comparing what the two attempts tell through [Attempt.writeTo], and [AttemptsFakeSpec] holds it to
 * the same suite as that adapter.
 */
class AttemptsFake : Attempts {
    private val kept = mutableMapOf<Uuid, Attempt>()

    override fun find(id: Uuid): Attempt? = kept[id]

    override fun save(id: Uuid, attempt: Attempt): AttemptSaveOutcome {
        val before = kept[id]
        if (before != null && !AttemptStory(attempt).continues(AttemptStory(before))) {
            return AttemptSaveOutcome.Superseded
        }
        kept[id] = attempt
        return AttemptSaveOutcome.Saved
    }

    override fun toString(): String = "AttemptsFake(kept=${kept.size})"
}
