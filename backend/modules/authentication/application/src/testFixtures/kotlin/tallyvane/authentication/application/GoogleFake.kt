package tallyvane.authentication.application

import tallyvane.authentication.application.port.Google

/**
 * [Google] with a table of the codes it has "sent", for tests of the code that uses the port (ADR-044).
 *
 * A code is worth what it was arranged to be worth, once, and only to the handshake it was arranged
 * for, which is how Google's own codes behave; [GoogleFakeSpec] holds it to the suite the adapter over
 * HTTP is held to. [goingDown] makes every exchange answer [GoogleAnswer.Unreachable].
 */
class GoogleFake : Google {
    private val codes = mutableMapOf<String, Pair<String, GoogleAnswer>>()
    private var down = false
    private var sent = 0

    fun goingDown() {
        down = true
    }

    /**
     * A code for the handshake whose state is [state], worth [answer].
     */
    fun arrange(state: String, answer: GoogleAnswer): String {
        sent += 1
        val code = "code-$sent"
        codes[code] = state to answer
        return code
    }

    override fun addressFor(handshake: GoogleHandshake): String = "https://google.test/auth?state=${stateOf(handshake)}"

    override suspend fun exchange(code: String, handshake: GoogleHandshake): GoogleAnswer {
        val arranged = codes[code]
        return when {
            down -> GoogleAnswer.Unreachable()
            arranged == null || arranged.first != stateOf(handshake) -> GoogleAnswer.Refused()
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
