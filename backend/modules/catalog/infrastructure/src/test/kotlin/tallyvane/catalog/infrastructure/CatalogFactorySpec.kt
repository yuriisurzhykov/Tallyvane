package tallyvane.catalog.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldNotBe

class CatalogFactorySpec :
    StringSpec(
        {
            "the catalog that ships holds together and has the role of the worked example" {
                val catalog = CatalogFactory().catalog()

                catalog.skills().size shouldBeGreaterThan 10
                catalog.role("staff-android") shouldNotBe null
                catalog.skills().map { it.slug } shouldContain "architecture"
            }
        },
    )
