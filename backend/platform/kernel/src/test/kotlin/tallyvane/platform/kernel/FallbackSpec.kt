package tallyvane.platform.kernel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.coroutines.cancellation.CancellationException

class FallbackSpec :
    StringSpec({
        "keeps the first successful attempt" {
            Fallback { "first" }
                .or { "second" }
                .orElse("default") shouldBe "first"
        }

        "does not run a later attempt once one succeeded" {
            var later = 0
            Fallback { "first" }
                .or {
                    later++
                    "second"
                }.orElse("default")
            later shouldBe 0
        }

        "moves on to the next attempt when one fails" {
            Fallback<String> { error("no") }
                .or { "second" }
                .orElse("default") shouldBe "second"
        }

        "takes the default when every attempt fails" {
            Fallback<String> { error("no") }
                .or { error("still no") }
                .orElse("default") shouldBe "default"
        }

        "treats a successful null as a value rather than a failure" {
            Fallback<String?> { null }
                .or { "second" }
                .orElse("default") shouldBe null
        }

        "rethrows cancellation instead of falling back" {
            shouldThrow<CancellationException> {
                Fallback<String> { throw CancellationException("cancelled") }
                    .or { "second" }
                    .orElse("default")
            }
        }

        "lets an Error propagate instead of falling back" {
            shouldThrow<StackOverflowError> {
                Fallback<String> { throw StackOverflowError("too deep") }
                    .or { "second" }
                    .orElse("default")
            }
        }

        "hands the cause to orRecover when every attempt failed" {
            Fallback<String> { error("why it broke") }
                .orRecover { cause -> cause.message.orEmpty() } shouldBe "why it broke"
        }

        "does not call orRecover when an attempt succeeded" {
            var recovered = 0
            Fallback { "first" }
                .orRecover {
                    recovered++
                    "recovered"
                } shouldBe "first"

            recovered shouldBe 0
        }

        "reports the last failure, not the first" {
            Fallback<String> { error("first failure") }
                .or { error("last failure") }
                .orRecover { cause -> cause.message.orEmpty() } shouldBe "last failure"
        }

        "keeps no cause once a later attempt succeeded" {
            Fallback<String> { error("recovered from") }
                .or { "second" }
                .orRecover { "should not be reached" } shouldBe "second"
        }

        "never hands cancellation to orRecover" {
            shouldThrow<CancellationException> {
                Fallback<String> { throw CancellationException("cancelled") }
                    .orRecover { "recovered" }
            }
        }
    })
