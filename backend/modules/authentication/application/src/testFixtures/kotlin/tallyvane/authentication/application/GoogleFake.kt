package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Surface

/**
 * [Google] with a table of the codes it has "sent", for tests of the code that uses the port (ADR-044).
 *
 * A code is worth what it was arranged to be worth, once, and only to the handshake it was arranged
 * for, which is how Google's own codes behave; [GoogleFakeSpec] holds it to the suite the adapter over
 * HTTP is held to. [goingDown] makes every exchange answer [GoogleAnswer.Unreachable].
 *
 * Google honours a code only at the redirect address it was asked to send the browser to, so the fake
 * remembers which door each handshake was sent from and refuses the code traded from the other.
 */
class GoogleFake : Google {
    private val codes = mutableMapOf<String, Pair<String, GoogleAnswer>>()
    private val sentFrom = mutableMapOf<String, Surface>()
    private var down = false
    private var sent = 0

    fun goingDown() {
        down = true
    }

    /**
     * A code for the handshake whose state is [state], worth [answer]. When [surface] is given it is the door
     * the code was sent to, whether or not the address was asked for through [addressFor].
     */
    fun arrange(state: String, answer: GoogleAnswer, surface: Surface? = null): String {
        surface?.let { sentFrom[state] = it }
        sent += 1
        val code = "code-$sent"
        codes[code] = state to answer
        return code
    }

    override fun addressFor(handshake: GoogleHandshake, surface: Surface): String {
        sentFrom[stateOf(handshake)] = surface
        return "https://google.test/auth?state=${stateOf(handshake)}"
    }

    override suspend fun exchange(code: String, handshake: GoogleHandshake, surface: Surface): GoogleAnswer {
        val arranged = codes[code]
        return when {
            down -> GoogleAnswer.Unreachable()
            arranged == null || arranged.first != stateOf(handshake) -> GoogleAnswer.Refused()
            sentFrom[arranged.first] != null && sentFrom[arranged.first] != surface -> GoogleAnswer.Refused()
            else -> arranged.second.also { codes.remove(code) }
        }
    }

    override fun toString(): String = "GoogleFake(codes=${codes.size})"

    private fun stateOf(handshake: GoogleHandshake): String {
        val told = mutableListOf<String>()
        handshake.writeTo { state, _, _ -> told += state.revealed() }
        return told.single()
    }
}
