package tallyvane.knowledge.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class QuoteAndOutcomeSpec :
    StringSpec(
        {
            val said = "Предложил кеш на Redis. Нагрузка на базу упала примерно на 40%."

            "a fragment that is in the answer, character for character, is a quote" {
                Utterance(said).cite("Нагрузка на базу упала примерно на 40%").shouldBeInstanceOf<Citation.Quoted>()
            }

            "a fragment is exact: part of a quote is a quote, the same words in another case are not" {
                Utterance(said).cite("Нагрузка на базу упала примерно на 40").shouldBeInstanceOf<Citation.Quoted>()
                Utterance(said).cite("нагрузка на базу упала").shouldBeInstanceOf<Citation.NotQuoted>()
            }

            "a fragment the answer does not contain, or an empty one, is not a quote" {
                Utterance(said).cite("Внедрил кеш на Memcached").shouldBeInstanceOf<Citation.NotQuoted>()
                Utterance(said).cite("").shouldBeInstanceOf<Citation.NotQuoted>()
                Utterance(said).cite("   ").shouldBeInstanceOf<Citation.NotQuoted>()
            }

            "a quote states a number that is written in it, and only that" {
                val quote = quoteOf(said, "упала примерно на 40%")
                quote.states(40.0) shouldBe true
                quote.states(4.0) shouldBe false
                quote.states(70.0) shouldBe false
            }

            "a quote reads a decimal written with a comma" {
                quoteOf("вырос на 2,5 раза", "на 2,5 раза").states(2.5) shouldBe true
            }

            "a digit inside a word is not a number" {
                quoteOf("перешли на S3 и p99", "S3 и p99").states(3.0) shouldBe false
            }

            "an outcome whose figure is not in its quote cannot be built" {
                shouldThrow<IllegalArgumentException> {
                    Outcome("database load", Direction.DOWN, 40.0, "%", true, quoteOf(said, "Предложил кеш на Redis"))
                }
            }

            "an outcome with a figure that is in its quote is a metric" {
                outcome().hasMetric() shouldBe true
            }

            "an outcome without a figure is not a metric" {
                outcome(said = "стало быстрее", what = "speed", direction = Direction.UP, value = null, unit = null)
                    .hasMetric() shouldBe false
            }

            "an outcome says itself the way a bullet does" {
                outcome().phrase() shouldBe "reducing database load by about 40%"
                outcome(approx = false, direction = Direction.UP, what = "throughput").phrase() shouldBe
                    "increasing throughput by 40%"
                outcome(direction = null).phrase() shouldBe "database load: about 40%"
            }

            "no metric said by the person is an outcome that is not a metric" {
                val unmeasured = Outcome.unmeasured(quoteOf("no metric", "no metric"))
                unmeasured.hasMetric() shouldBe false
            }

            "action kinds are read by their wire names and no others" {
                ActionKind.isKnown("designed") shouldBe true
                ActionKind.isKnown("Designed") shouldBe false
                ActionKind.isKnown("invented") shouldBe false
                ActionKind.named("led") shouldBe ActionKind.LED
            }
        },
    )
