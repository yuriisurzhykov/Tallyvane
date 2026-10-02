package tallyvane.authentication.application

import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleHandshakes
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.authentication.domain.Attempt
import tallyvane.platform.kernel.Digest

/**
 * What is kept about sign-ins, in maps, for tests of the code that uses the ports (ADR-044).
 *
 * One class stands behind three ports because Postgres does: a handshake and a profile belong to an
 * attempt and go when it goes, and keeping them apart here would leave that rule to the tests of every
 * use case. Each port is still held to its own suite, by [AttemptsFakeSpec], [GoogleHandshakesFakeSpec]
 * and [GoogleProfilesFakeSpec].
 *
 * Judges "does this attempt contain what is kept?" exactly as the adapter over Postgres does, by
 * comparing what the two attempts tell through [Attempt.writeTo].
 */
class SignInsFake :
    Attempts,
    GoogleHandshakes,
    GoogleProfiles {
    private val attempts = mutableMapOf<Digest, Attempt>()
    private val handshakes = mutableMapOf<Digest, GoogleHandshake>()
    private val profiles = mutableMapOf<Digest, GoogleProfile>()

    override fun find(key: Digest): Attempt? = attempts[key]

    override fun save(key: Digest, attempt: Attempt): AttemptSaveOutcome {
        val before = attempts[key]
        if (before != null && !AttemptStory(attempt).continues(AttemptStory(before))) {
            return AttemptSaveOutcome.Superseded
        }
        attempts[key] = attempt
        return AttemptSaveOutcome.Saved
    }

    override fun forget(key: Digest) {
        attempts.remove(key)
        handshakes.remove(key)
        profiles.remove(key)
    }

    override fun keep(attempt: Digest, handshake: GoogleHandshake) {
        check(attempt in attempts) { "A handshake is kept for an attempt that is kept." }
        handshakes[attempt] = handshake
    }

    override fun take(attempt: Digest): GoogleHandshake? = handshakes.remove(attempt)

    override fun keep(attempt: Digest, profile: GoogleProfile) {
        check(attempt in attempts) { "A profile is kept for an attempt that is kept." }
        profiles[attempt] = profile
    }

    override fun of(attempt: Digest): GoogleProfile? = profiles[attempt]

    override fun toString(): String = "SignInsFake(attempts=${attempts.size})"
}
