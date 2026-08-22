package tallyvane.gradle.graph.manifest

internal class YamlMap(
    private val values: Map<*, *>,
    private val origin: String,
) {
    fun required(key: String): YamlMap = optional(key) ?: error("$origin is missing '$key'")

    fun optional(key: String): YamlMap? {
        val value = values[key] ?: return null
        val nested = value as? Map<*, *> ?: error("$origin.$key must be a mapping")
        return YamlMap(nested, "$origin.$key")
    }

    fun stringList(key: String): List<String> {
        val value = values[key] ?: return emptyList()
        val list = value as? List<*> ?: error("$origin.$key must be a list")
        return list.map { item ->
            item as? String ?: error("$origin.$key entries must be strings")
        }
    }

    fun stringListMap(key: String): Map<String, List<String>> {
        val nested = required(key)
        return nested.keys().associateWith { child -> nested.stringList(child) }
    }

    fun keys(): Set<String> =
        values.keys
            .map { key ->
                key as? String ?: error("$origin keys must be strings")
            }.toSet()

    fun children(): Map<String, YamlMap> = keys().associateWith { key -> required(key) }
}
