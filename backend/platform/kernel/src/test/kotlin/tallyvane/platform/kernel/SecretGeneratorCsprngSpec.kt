package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.util.Base64

class SecretGeneratorCsprngSpec :
    StringSpec(
        {
            "a secret carries 256 bits in the URL-safe alphabet without padding" {
                val secret = SecretGenerator.Csprng().next().revealed()

                secret shouldMatch Regex("^[A-Za-z0-9_-]{43}$")
                Base64.getUrlDecoder().decode(secret).size shouldBe 32
            }

            "no two secrets of a thousand are the same" {
                val generator = SecretGenerator.Csprng()

                List(1000) { generator.next().revealed() }.toSet().size shouldBe 1000
            }
        },
    )
