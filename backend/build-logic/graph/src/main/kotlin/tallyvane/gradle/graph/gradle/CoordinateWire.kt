package tallyvane.gradle.graph.gradle

import tallyvane.gradle.graph.rules.DeclaredCoordinate

internal class CoordinateWire {
    fun encode(coordinate: DeclaredCoordinate): String = "${coordinate.projectPath}\t${coordinate.group}"

    fun decode(value: String): DeclaredCoordinate {
        val separator = value.indexOf('\t')
        check(separator >= 0) { "Coordinate wire must be path<TAB>group: $value" }
        return DeclaredCoordinate(
            projectPath = value.substring(0, separator),
            group = value.substring(separator + 1),
        )
    }
}
