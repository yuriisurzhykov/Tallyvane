package tallyvane.identity.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EmailSpec : StringSpec(
    {
        "accepts an ordinary email address" {
            Email("").value shouldBe ""
        }
    },
)
