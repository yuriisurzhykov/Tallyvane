package tallyvane.identity.domain.credential

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GoogleSubjectSpec :
    StringSpec({
        "accepts an ordinary subject" {
            GoogleSubject("108234567890123456789").value shouldBe "108234567890123456789"
        }

        "accepts a subject at exactly the length limit" {
            GoogleSubject("a".repeat(255)).value.length shouldBe 255
        }

        "rejects a blank subject" {
            shouldThrow<IllegalArgumentException> { GoogleSubject("   ") }
        }

        "rejects an empty subject" {
            shouldThrow<IllegalArgumentException> { GoogleSubject("") }
        }

        "rejects a subject one character past the length limit" {
            shouldThrow<IllegalArgumentException> { GoogleSubject("a".repeat(256)) }
        }
    })
