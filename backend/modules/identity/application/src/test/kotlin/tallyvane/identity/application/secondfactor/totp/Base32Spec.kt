package tallyvane.identity.application.secondfactor.totp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * RFC 4648 §10's own published test vectors — the standard's answer, not a value this repository
 * computed and is now trusting itself on.
 */
class Base32Spec :
    StringSpec({
        val base32 = Base32()
        val vectors = mapOf(
            "" to "",
            "f" to "MY======",
            "fo" to "MZXQ====",
            "foo" to "MZXW6===",
            "foob" to "MZXW6YQ=",
            "fooba" to "MZXW6YTB",
            "foobar" to "MZXW6YTBOI======",
        )

        "encodes every RFC 4648 test vector exactly" {
            for ((input, expected) in vectors) {
                base32.encode(input.toByteArray(Charsets.US_ASCII)) shouldBe expected
            }
        }

        "decodes every RFC 4648 test vector back to its original bytes" {
            for ((expected, encoded) in vectors) {
                base32.decode(encoded).toString(Charsets.US_ASCII) shouldBe expected
            }
        }

        "decoding is case-insensitive, matching what a human might retype from a screen" {
            base32.decode("mzxw6ytb").toString(Charsets.US_ASCII) shouldBe "fooba"
        }

        "a 20-byte secret round-trips through encode and decode unchanged" {
            val secret = ByteArray(20) { it.toByte() }

            base32.decode(base32.encode(secret)) shouldBe secret
        }
    })
