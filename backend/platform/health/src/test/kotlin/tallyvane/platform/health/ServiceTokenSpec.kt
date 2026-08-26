package tallyvane.platform.health

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ServiceTokenSpec :
    StringSpec(
        {
            "admits the exact secret" {
                ServiceToken("s3cret").admits("s3cret") shouldBe true
            }

            "refuses a different secret of the same length" {
                ServiceToken("s3cret").admits("s3cr3t") shouldBe false
            }

            "refuses a prefix, which is what a character-by-character guess looks like" {
                ServiceToken("s3cret").admits("s3c") shouldBe false
            }

            "refuses an absent header" {
                ServiceToken("s3cret").admits(null) shouldBe false
            }

            // A deploy that forgot to supply the secret must not end up with an open door. The
            // failure mode of a missing setting is "no access", never "all access".
            "an unset secret admits nobody, including an empty header" {
                ServiceToken("").admits("") shouldBe false
                ServiceToken("").admits(null) shouldBe false
                ServiceToken("").admits("anything") shouldBe false
            }
        },
    )
