package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-03T09:00:00Z")
private val FIRST = 1.seconds

class AccountGuessesSpec :
    StringSpec(
        {
            "an account with no wrong code lately has nothing to wait for" {
                AccountGuesses.at(emptyList()).pauseLeft(NOW, FIRST) shouldBe Duration.ZERO
            }

            "the first wrong code earns the first delay, counted from that code" {
                val guesses = AccountGuesses.at(listOf(NOW))

                guesses.pauseLeft(NOW, FIRST) shouldBe 1.seconds
                guesses.pauseLeft(NOW + 400.milliseconds, FIRST) shouldBe 600.milliseconds
                guesses.pauseLeft(NOW + 1.seconds, FIRST) shouldBe Duration.ZERO
            }

            "each further wrong code doubles it" {
                val guesses = AccountGuesses.at(listOf(NOW - 10.seconds, NOW - 5.seconds, NOW))

                guesses.pauseLeft(NOW, FIRST) shouldBe 4.seconds
            }

            "it never passes five minutes, however many wrong codes there were" {
                val guesses = AccountGuesses.at((0 until 40).map { NOW - it.seconds })

                guesses.pauseLeft(NOW, FIRST) shouldBe 5.minutes
            }

            "a wrong code older than the window no longer counts" {
                val guesses = AccountGuesses.at(listOf(NOW - AccountGuesses.WINDOW - 1.seconds))

                guesses.pauseLeft(NOW, FIRST) shouldBe Duration.ZERO
            }

            "the order the times come in does not matter" {
                val ordered = AccountGuesses.at(listOf(NOW - 10.seconds, NOW - 5.seconds, NOW))
                val shuffled = AccountGuesses.at(listOf(NOW, NOW - 10.seconds, NOW - 5.seconds))

                shuffled.pauseLeft(NOW, FIRST) shouldBe ordered.pauseLeft(NOW, FIRST)
            }
        },
    )
