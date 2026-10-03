package tallyvane.authentication.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Checked against RFC 4648 section 10's own vectors, without the padding the application leaves off.
 */
class Base32Spec :
    StringSpec(
        {
            val vectors = mapOf(
                "" to "",
                "f" to "MY",
                "fo" to "MZXQ",
                "foo" to "MZXW6",
                "foob" to "MZXW6YQ",
                "fooba" to "MZXW6YTB",
                "foobar" to "MZXW6YTBOI",
            )

            "it writes what RFC 4648 says" {
                vectors.forEach { (plain, spelled) ->
                    Base32().encode(plain.toByteArray(Charsets.US_ASCII)) shouldBe spelled
                }
            }

            "it reads what it wrote, in either case and with padding" {
                vectors.forEach { (plain, spelled) ->
                    String(Base32().decode(spelled), Charsets.US_ASCII) shouldBe plain
                    String(Base32().decode(spelled.lowercase() + "==="), Charsets.US_ASCII) shouldBe plain
                }
            }

            "a character outside the alphabet is refused" {
                shouldThrow<IllegalArgumentException> { Base32().decode("MZXW1") }
            }
        },
    )
