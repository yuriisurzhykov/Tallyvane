package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.authentication.domain.Attempt
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Surface

/**
 * One sign-in on its way back from Google: the [attempt] kept under [key], and the [handshake] it
 * went with, already taken from storage so it cannot be used again.
 */
internal class Trip(val key: Digest, val attempt: Attempt, private val handshake: GoogleHandshake) {
    /**
     * Whether [state] is the one this trip sent to Google.
     */
    fun answers(state: String): Boolean = handshake.answers(state)

    /**
     * Trades [code], which came back through [surface], with [google] under this trip's handshake.
     */
    suspend fun exchangeWith(google: Google, code: String, surface: Surface): GoogleAnswer =
        google.exchange(code, handshake, surface)

    override fun toString(): String = "Trip($key, $attempt)"
}
