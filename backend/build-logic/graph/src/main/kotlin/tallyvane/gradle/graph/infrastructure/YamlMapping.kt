package tallyvane.gradle.graph.infrastructure

/**
 * A YAML mapping with typed lookups and errors that include [origin].
 *
 * SnakeYAML yields `Map<*, *>`. This is the one place those values are
 * cast to nested mappings, string lists, and string keys, so
 * [YamlModulesYaml] can read `depends` without repeating `as?` at every
 * key.
 *
 * @param values Mapping at this location in the document.
 * @param origin Path of keys from the document root (`modules.yaml`,
 * `modules.yaml.platform.kernel`), used in every error.
 * @throws IllegalStateException from every lookup that finds the wrong
 * shape (a list where a mapping is required, a non-string key, …).
 */
internal class YamlMapping(
    private val values: Map<*, *>,
    private val origin: String,
) {
    /**
     * Nested mapping at [key].
     *
     * @param key Child key that must be present and hold a mapping.
     * @throws IllegalStateException if [key] is missing or is not a mapping.
     */
    fun required(key: String): YamlMapping = optional(key) ?: error("$origin is missing '$key'")

    /**
     * Nested mapping at [key], if present.
     *
     * @param key Child key.
     * @return The nested mapping, or `null` if [key] is absent.
     * @throws IllegalStateException if [key] is present but is not a mapping.
     */
    fun optional(key: String): YamlMapping? {
        val value = values[key] ?: return null
        val nested = value as? Map<*, *> ?: error("$origin.$key must be a mapping")
        return YamlMapping(nested, "$origin.$key")
    }

    /**
     * String list at [key].
     *
     * @param key Child key (`depends`, `layers`, `reads`).
     * @return The list, or empty if [key] is absent.
     * @throws IllegalStateException if [key] is present but is not a list of strings.
     */
    fun stringList(key: String): List<String> {
        val value = values[key] ?: return emptyList()
        val list = value as? List<*> ?: error("$origin.$key must be a list")
        return list.map { item ->
            item as? String ?: error("$origin.$key entries must be strings")
        }
    }

    /**
     * Nested mapping whose values are string lists, as top-level `layers:` is.
     *
     * @param key Child key that must be a mapping of string lists (`layers`).
     * @return Each child key to its string list. Missing child lists are empty (see [stringList]).
     * @throws IllegalStateException if [key] is missing or is not a mapping.
     */
    fun stringListMap(key: String): Map<String, List<String>> {
        val nested = required(key)
        return nested.keys().associateWith { child -> nested.stringList(child) }
    }

    /**
     * String keys of this mapping.
     *
     * @throws IllegalStateException if any key is not a string.
     */
    fun keys(): Set<String> =
        values.keys
            .map { key ->
                key as? String ?: error("$origin keys must be strings")
            }.toSet()

    /**
     * Each key paired with its nested mapping.
     *
     * @return Insertion-independent map of [keys] to [required] children.
     * @throws IllegalStateException if a value is not a mapping.
     */
    fun children(): Map<String, YamlMapping> = keys().associateWith { key -> required(key) }
}
