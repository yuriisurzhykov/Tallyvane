package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IdGeneratorFakeSpec :
    StringSpec({
        "returns a known sequence" {
            val ids = IdGeneratorFake()
            ids.next() shouldBe "id-1"
            ids.next() shouldBe "id-2"
        }
    })
