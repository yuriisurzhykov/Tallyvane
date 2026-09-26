package tallyvane.identity.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EmailSpec :
    StringSpec({
        "accepts an ordinary address" {
            Email("person@example.com").value shouldBe "person@example.com"
        }

        "accepts a local part with a dot and a plus tag" {
            Email("first.last+tag@example.com").value shouldBe "first.last+tag@example.com"
        }

        "accepts a domain with more than one label" {
            Email("person@mail.example.co.uk").value shouldBe "person@mail.example.co.uk"
        }

        "rejects a value with no @" {
            shouldThrow<IllegalArgumentException> { Email("person.example.com") }
        }

        "rejects a value with no domain after @" {
            shouldThrow<IllegalArgumentException> { Email("person@") }
        }

        "rejects a value with no local part before @" {
            shouldThrow<IllegalArgumentException> { Email("@example.com") }
        }

        "rejects a domain with no dot, so no top-level domain" {
            shouldThrow<IllegalArgumentException> { Email("person@example") }
        }

        "rejects an empty string" {
            shouldThrow<IllegalArgumentException> { Email("") }
        }

        "rejects a value that is only whitespace" {
            shouldThrow<IllegalArgumentException> { Email("   ") }
        }

        "rejects a local part containing whitespace" {
            shouldThrow<IllegalArgumentException> { Email("first last@example.com") }
        }

        "rejects a domain containing whitespace" {
            shouldThrow<IllegalArgumentException> { Email("person@example .com") }
        }

        "rejects a value with two @ characters" {
            shouldThrow<IllegalArgumentException> { Email("person@@example.com") }
        }

        "treats two differently-cased addresses as different values" {
            // Deliberate: case-insensitive lookup is a Postgres column collation
            // (`platform.case_insensitive`), not normalisation inside this value object — see
            // this file's own KDoc for why folding case here would disagree with the database.
            Email("Person@example.com") shouldBe Email("Person@example.com")
            (Email("Person@example.com") == Email("person@example.com")) shouldBe false
        }
    })
