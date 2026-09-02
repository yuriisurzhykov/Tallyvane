package tallyvane.identity.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DeviceLabelSpec :
    StringSpec({
        "accepts an ordinary label" {
            DeviceLabel("Chrome on MacBook").value shouldBe "Chrome on MacBook"
        }

        "accepts a label at exactly the length limit" {
            DeviceLabel("a".repeat(120)).value.length shouldBe 120
        }

        "rejects a blank label" {
            shouldThrow<IllegalArgumentException> { DeviceLabel("   ") }
        }

        "rejects an empty label" {
            shouldThrow<IllegalArgumentException> { DeviceLabel("") }
        }

        "rejects a label one character past the length limit" {
            shouldThrow<IllegalArgumentException> { DeviceLabel("a".repeat(121)) }
        }
    })
