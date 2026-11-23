package tallyvane.knowledge.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class NothingMoreSpec :
    StringSpec(
        {
            val nothingMore = NothingMore()

            "short answers with a keyword say there is nothing more" {
                listOf("нет", "Нет, больше ничего", "ничего", "no", "No.", "nothing", "That's all", "nothing more").forEach {
                    nothingMore.isSaidBy(it) shouldBe true
                }
            }

            "a keyword inside another word does not count" {
                listOf("интернет", "Nobody else", "nothingness", "ничегошеньки").forEach {
                    nothingMore.isSaidBy(it) shouldBe false
                }
            }

            "a long answer that happens to contain a keyword is an answer" {
                nothingMore.isSaidBy("нет проблем с кешем, ещё я добавил consumer для Kafka") shouldBe false
                nothingMore.isSaidBy("No, but I also rewrote the importer in Go last year") shouldBe false
            }

            "an answer without a keyword is an answer" {
                nothingMore.isSaidBy("Redis") shouldBe false
                nothingMore.isSaidBy("") shouldBe false
            }
        },
    )
