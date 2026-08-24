package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

class IdGeneratorFakeSpec :
    StringSpec({
        "returns a known sequence" {
            val ids = IdGeneratorFake()
            ids.next() shouldBe Uuid.parse("00000000-0000-7000-8000-000000000001")
            ids.next() shouldBe Uuid.parse("00000000-0000-7000-8000-000000000002")
        }

        "yields ids a caller may sort by mint order" {
            val ids = IdGeneratorFake()
            val minted = List(3) { ids.next() }

            minted.sorted() shouldBe minted
        }

        "claims version 7, so a caller reading the version is not misled" {
            IdGeneratorFake().next().toString().substring(14, 15) shouldBe "7"
        }
    })
