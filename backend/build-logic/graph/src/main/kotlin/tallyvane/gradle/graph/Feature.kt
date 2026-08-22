package tallyvane.gradle.graph

internal interface Feature {
    fun name(): String

    fun path(layer: String): String

    fun hasLayer(layer: String): Boolean

    fun layerNames(): List<String>

    fun readContractPaths(): List<String>

    fun isOwnModule(path: String): Boolean

    class FromManifest(
        private val name: String,
        private val layers: List<String>,
        private val reads: List<String>,
    ) : Feature {
        override fun name(): String = name

        override fun path(layer: String): String = ":modules:$name:$layer"

        override fun hasLayer(layer: String): Boolean = layer in layers

        override fun layerNames(): List<String> = layers

        override fun readContractPaths(): List<String> =
            reads.map { read -> ":modules:$read:contract" }

        override fun isOwnModule(path: String): Boolean = path.startsWith(":modules:$name:")
    }
}
