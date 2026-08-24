package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class IdGeneratorUuid7Spec :
    StringSpec(
        {
            "mints version 7, which is what §8.1 asks the database to cluster on" {
                IdGenerator.Uuid7().next().toString().substring(14, 15) shouldBe "7"
            }

            "never repeats an id" {
                val ids = IdGenerator.Uuid7()

                val minted = List(1_000) { ids.next() }

                minted.toSet().size shouldBe minted.size
            }

            "orders successive ids by mint time, so an index stays clustered" {
                val ids = IdGenerator.Uuid7()

                val minted = List(100) { ids.next() }

                minted.sorted() shouldBe minted
            }

            "does not mint the fake's sequence, so a fake cannot pass for production" {
                IdGenerator.Uuid7().next() shouldNotBe IdGeneratorFake().next()
            }

            "carries entropy: two generators started together disagree" {
                IdGenerator.Uuid7().next() shouldNotBe IdGenerator.Uuid7().next()
            }

            "is not a secret source: the mint time is readable from the id" {
                val ids = IdGenerator.Uuid7()

                val earlier = ids.next()
                val later = ids.next()

                timestampOf(later) shouldBeGreaterThan (timestampOf(earlier) - 1L)
            }
        },
    )

/**
 * The 48-bit millisecond prefix a v7 id publishes about itself.
 */
private fun timestampOf(id: kotlin.uuid.Uuid): Long = id.toString().replace("-", "").substring(0, 12).toLong(radix = 16)
