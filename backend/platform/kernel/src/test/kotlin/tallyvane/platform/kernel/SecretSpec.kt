package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val VALUE = "hunter2-and-then-some-more-characters"

class SecretSpec :
    StringSpec(
        {
            "gives the value back to whoever needs it" {
                Secret(VALUE).revealed() shouldBe VALUE
            }

            // The case this type exists for. `DatabaseAccess` was a data class with a plain
            // String password, so any log line or exception message carrying the whole object
            // printed the password — §17 forbids exactly that, and nothing stopped it.
            "does not print the value" {
                Secret(VALUE).toString() shouldNotContain VALUE
            }

            "prints something, so a redacted field is visibly a field" {
                Secret(VALUE).toString() shouldContain "*"
            }

            // Value equality, not identity: `DatabaseAccess` is a data class, and two
            // separately built instances describing the same database must still compare equal.
            "compares by value" {
                Secret(VALUE) shouldBe Secret(VALUE)
                Secret(VALUE) shouldNotBe Secret("something else entirely, of the same length")
            }

            "hashes by value, so it survives a map or a set" {
                Secret(VALUE).hashCode() shouldBe Secret(VALUE).hashCode()
            }

            // Comparing secrets of different lengths must not be the fast path that leaks the
            // length of the expected one through timing. The lengths are compared, not
            // short-circuited on the first differing character.
            "answers false for a value of a different length" {
                Secret(VALUE) shouldNotBe Secret(VALUE.dropLast(1))
            }
        },
    )
