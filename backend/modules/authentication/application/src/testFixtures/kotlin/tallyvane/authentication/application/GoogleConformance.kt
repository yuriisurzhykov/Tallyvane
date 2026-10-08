package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface

private fun handshake(word: String) =
    GoogleHandshake(Secret("state-$word"), Secret("nonce-$word"), Secret("verifier-$word"))

private fun stateOf(handshake: GoogleHandshake): String {
    val told = mutableListOf<String>()
    handshake.writeTo { state, _, _ -> told += state.revealed() }
    return told.single()
}

/**
 * What a [GoogleAnswer] said, as one line, so a suite can compare answers.
 */
private class Line : GoogleAnswer.Report<String> {
    override fun vouched(subject: String, profile: GoogleProfile): String {
        val told = mutableListOf<String>()
        profile.writeTo { name, email -> told += "$name <$email>" }
        return "vouched $subject ${told.single()}"
    }

    override fun emailUnverified(): String = "email unverified"

    override fun refused(): String = "refused"

    override fun unreachable(): String = "unreachable"
}

/**
 * The behaviour every [Google] must show, whatever speaks to Google.
 *
 * Google is the one thing here that cannot be run for real in a test, so what a code is worth is
 * arranged through the [Subject]: the fake keeps a table, and the adapter's suite runs a provider of
 * its own that signs tokens as Google does. Both must answer the same way.
 */
abstract class GoogleConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    /**
     * A [Google] and a way to obtain codes it will honour.
     */
    interface Subject {
        val google: Google

        /**
         * A code Google would send to [handshake]'s redirect on [surface] after [subject], called [name] with
         * the verified address [email], signed in.
         */
        suspend fun codeFor(
            handshake: GoogleHandshake,
            subject: String,
            name: String,
            email: String,
            surface: Surface = Surface.App,
        ): String

        /**
         * The same, for a person whose address Google has not verified.
         */
        suspend fun unverifiedCodeFor(handshake: GoogleHandshake, subject: String): String
    }

    private suspend fun Subject.exchange(
        code: String,
        handshake: GoogleHandshake,
        surface: Surface = Surface.App,
    ): String = google.exchange(code, handshake, surface).reportTo(Line())

    init {
        "sends the person to an address that carries this handshake's state and no other's" {
            val subject = fresh()

            val address = subject.google.addressFor(handshake("a"), Surface.App)

            address shouldContain stateOf(handshake("a"))
            address shouldNotContain stateOf(handshake("b"))
        }

        "vouches for the person a genuine code was sent for" {
            val subject = fresh()
            val code = subject.codeFor(handshake("a"), "sub-1", "Ann Example", "ann@example.com")

            subject.exchange(code, handshake("a")) shouldBe "vouched sub-1 Ann Example <ann@example.com>"
        }

        "refuses a code nobody sent" {
            fresh().exchange("made-up-code", handshake("a")) shouldBe "refused"
        }

        "refuses a code sent for another handshake" {
            val subject = fresh()
            val code = subject.codeFor(handshake("a"), "sub-1", "Ann Example", "ann@example.com")

            subject.exchange(code, handshake("b")) shouldBe "refused"
        }

        "refuses a code that is traded against the other door's address than the one it was sent to" {
            val subject = fresh()
            val code = subject.codeFor(handshake("a"), "sub-1", "Ann Example", "ann@example.com", Surface.Admin)

            subject.exchange(code, handshake("a"), Surface.App) shouldBe "refused"
        }

        "vouches for the person on the administrators' door when the code was sent to that door" {
            val subject = fresh()
            val code = subject.codeFor(handshake("a"), "sub-1", "Ann Example", "ann@example.com", Surface.Admin)

            subject.exchange(code, handshake("a"), Surface.Admin) shouldBe "vouched sub-1 Ann Example <ann@example.com>"
        }

        "refuses a code the second time" {
            val subject = fresh()
            val code = subject.codeFor(handshake("a"), "sub-1", "Ann Example", "ann@example.com")
            subject.exchange(code, handshake("a"))

            subject.exchange(code, handshake("a")) shouldBe "refused"
        }

        "says so when the address was not verified" {
            val subject = fresh()
            val code = subject.unverifiedCodeFor(handshake("a"), "sub-1")

            subject.exchange(code, handshake("a")) shouldBe "email unverified"
        }
    }
}
