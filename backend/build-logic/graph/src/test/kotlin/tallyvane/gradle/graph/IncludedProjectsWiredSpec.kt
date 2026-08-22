package tallyvane.gradle.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IncludedProjectsWiredSpec :
    StringSpec({
        "round-trips a coordinate through the Gradle input string" {
            val original = IncludedProjects.Coordinate(":platform:kernel", "org.jetbrains.kotlin")
            val wired =
                IncludedProjects.Wired(
                    included = setOf(":platform:kernel"),
                    dependencies = mapOf(":platform:kernel" to emptySet()),
                    wiredCoordinates = listOf("${original.projectPath()}\t${original.group()}"),
                )
            val decoded = wired.coordinates().single()
            decoded.projectPath() shouldBe original.projectPath()
            decoded.group() shouldBe original.group()
        }
    })
