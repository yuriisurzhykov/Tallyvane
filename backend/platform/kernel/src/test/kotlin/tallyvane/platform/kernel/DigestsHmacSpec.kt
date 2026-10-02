package tallyvane.platform.kernel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain

private const val PEPPER = "a-pepper-long-enough-to-be-accepted-here"

class DigestsHmacSpec :
    StringSpec(
        {
            "the digest is HMAC-SHA256 of the secret under the pepper" {
                // Computed outside this code, by
                // `printf secret | openssl dgst -sha256 -hmac a-pepper-long-enough-to-be-accepted-here`.
                val expected = "68efe4759a498f5b054b2093ad6952f6749c7c4f14602a1f80821ea7d92c9b16"
                val told = mutableListOf<String>()

                Digests.Hmac(Secret(PEPPER), 1).of(Secret("secret")).writeTo { bytes, _ ->
                    told += bytes.joinToString("") { "%02x".format(it) }
                }

                told shouldBe listOf(expected)
            }

            "the same secret gives the same digest, a different one or a different pepper does not" {
                val digests = Digests.Hmac(Secret(PEPPER), 1)

                digests.of(Secret("one")) shouldBe digests.of(Secret("one"))
                digests.of(Secret("one")) shouldNotBe digests.of(Secret("two"))
                Digests.Hmac(Secret(PEPPER.reversed()), 1).of(Secret("one")) shouldNotBe digests.of(Secret("one"))
            }

            "the pepper version travels with the digest and tells two keys apart" {
                val versions = mutableListOf<Int>()

                Digests.Hmac(Secret(PEPPER), 3).of(Secret("one")).writeTo { _, version -> versions += version }

                versions shouldBe listOf(3)
                Digests.Hmac(Secret(PEPPER), 3).of(Secret("one")) shouldNotBe
                    Digests.Hmac(Secret(PEPPER), 4).of(Secret("one"))
            }

            "a short pepper is refused before it makes a single digest" {
                shouldThrow<IllegalArgumentException> { Digests.Hmac(Secret("short"), 1) }
            }

            "a digest printed in a log names its version and none of its bytes" {
                Digests.Hmac(Secret(PEPPER), 2).of(Secret("one")).toString() shouldNotContain "["
                Digests.Hmac(Secret(PEPPER), 2).of(Secret("one")).toString() shouldBe "Digest(version=2)"
            }
        },
    )
