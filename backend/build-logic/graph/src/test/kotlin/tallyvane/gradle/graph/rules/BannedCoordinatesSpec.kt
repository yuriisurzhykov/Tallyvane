package tallyvane.gradle.graph.rules

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BannedCoordinatesSpec : StringSpec({
    "flags mockk and nested mockito groups" {
        val findings =
            BannedCoordinates().findings(
                listOf(
                    DeclaredCoordinate(":jobs", "io.mockk"),
                    DeclaredCoordinate(":jobs", "org.mockito.kotlin"),
                    DeclaredCoordinate(":jobs", "org.jetbrains.kotlin"),
                ),
            )
        findings shouldBe
            listOf(
                GraphFinding("Banned coordinate io.mockk in :jobs"),
                GraphFinding("Banned coordinate org.mockito.kotlin in :jobs"),
            )
    }
})
