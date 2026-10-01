package tallyvane.authentication.application

import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import kotlin.time.Instant

/**
 * Everything an [Attempt] says when asked to [Attempt.writeTo], kept as lines so two attempts can be
 * compared by what they tell and nothing else.
 *
 * An attempt has no `equals` and gives out no fields, so this is how a suite asks "is this the
 * attempt I saved?" without a getter: it listens, exactly as storage does.
 *
 * Instants are cut to the microsecond, the precision Postgres keeps, so one comparison serves every
 * implementation of the port.
 */
class AttemptStory private constructor(
    private val beginning: List<String>,
    private val verified: List<String>,
    private val failed: List<String>,
) {
    constructor(attempt: Attempt) : this(Listener().also { attempt.writeTo(it) })

    private constructor(listener: Listener) : this(
        listener.startLines(),
        listener.verifiedLines(),
        listener.failedLines(),
    )

    /**
     * Whether this story is the other's with nothing missing and nothing changed: the same start, and
     * every factor and wrong answer of [shorter], in order, at the front of this one's.
     */
    fun continues(shorter: AttemptStory): Boolean = beginning == shorter.beginning &&
        verified.take(shorter.verified.size) == shorter.verified &&
        failed.take(shorter.failed.size) == shorter.failed

    override fun equals(other: Any?): Boolean = other is AttemptStory && continues(other) && other.continues(this)

    override fun hashCode(): Int = listOf(beginning, verified, failed).hashCode()

    override fun toString(): String = "AttemptStory(beginning=$beginning, verified=$verified, failed=$failed)"

    private class Listener : Attempt.Record {
        private companion object {
            const val NANOSECONDS_PER_MICROSECOND = 1000
        }

        private val started = mutableListOf<String>()
        private val verified = mutableListOf<String>()
        private val failed = mutableListOf<String>()

        override fun started(purpose: Purpose, at: Instant) {
            started += "$purpose ${at.toMicroseconds()}"
        }

        override fun verified(kind: FactorKind, at: Instant) {
            verified += "$kind ${at.toMicroseconds()}"
        }

        override fun failed(at: Instant) {
            failed += at.toMicroseconds().toString()
        }

        fun startLines(): List<String> = started.toList()

        fun verifiedLines(): List<String> = verified.toList()

        fun failedLines(): List<String> = failed.toList()

        private fun Instant.toMicroseconds(): Instant = Instant.fromEpochSeconds(
            epochSeconds,
            nanosecondsOfSecond / NANOSECONDS_PER_MICROSECOND * NANOSECONDS_PER_MICROSECOND,
        )
    }
}
