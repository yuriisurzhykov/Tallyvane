package tallyvane.platform.persistence

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DriverPropertiesSpec :
    StringSpec(
        {
            // The whole point of the type. An Int reaches pgjdbc as an Integer, and
            // Properties.getProperty - which is how pgjdbc reads its settings - returns null
            // for it. Measured in playground/timeout-bounds: the bound never fired.
            "stores a number as a String, because that is the only form the driver reads" {
                val configuration = HikariConfig()

                DriverProperties(configuration).set("socketTimeout", 30)

                val stored = configuration.dataSourceProperties["socketTimeout"]
                stored.shouldBeInstanceOf<String>()
                stored shouldBe "30"
            }

            "reads back through getProperty, which is the call that silently drops an Int" {
                val configuration = HikariConfig()

                DriverProperties(configuration).set("connectTimeout", 5)

                configuration.dataSourceProperties.getProperty("connectTimeout") shouldBe "5"
            }

            "leaves a String value as it is" {
                val configuration = HikariConfig()

                DriverProperties(configuration).set("options", "-c lock_timeout=3000ms")

                configuration.dataSourceProperties.getProperty("options") shouldBe "-c lock_timeout=3000ms"
            }
        },
    )
