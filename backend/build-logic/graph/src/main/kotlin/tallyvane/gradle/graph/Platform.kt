package tallyvane.gradle.graph

internal interface Platform {
    fun name(): String

    fun path(): String

    fun expectedPaths(): List<String>

    class FromManifest(
        private val name: String,
        private val depends: List<String>,
    ) : Platform {
        override fun name(): String = name

        override fun path(): String = pathOf(name)

        override fun expectedPaths(): List<String> = depends.map(::pathOf)

        private fun pathOf(platformName: String): String =
            ":platform:${platformName.removePrefix("platform:")}"
    }
}
