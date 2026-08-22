package tallyvane.gradle.graph.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.gradle.graph.rules.DeclaredCoordinate

class CoordinateWireSpec : StringSpec({
    "round-trips a coordinate through the Gradle input string" {
        val wire = CoordinateWire()
        val original = DeclaredCoordinate(":platform:kernel", "org.jetbrains.kotlin")
        wire.decode(wire.encode(original)) shouldBe original
    }
})
