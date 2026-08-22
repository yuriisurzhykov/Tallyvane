package tallyvane.gradle.graph.rules

internal data class GradleModulePath(
    val value: String,
) {
    companion object {
        fun platform(name: String): GradleModulePath {
            val platformName = name.removePrefix("platform:")
            return GradleModulePath(":platform:$platformName")
        }

        fun feature(
            module: String,
            layer: String,
        ): GradleModulePath = GradleModulePath(":modules:$module:$layer")
    }
}
