package tallyvane.gradle.graph.domain

/**
 * The graph declared in `modules.yaml`: planned capabilities, platforms,
 * live features, and the per-layer allow-list tokens.
 *
 * This is what [GraphCheck] reads. It does not parse YAML; the production
 * implementation is [tallyvane.gradle.graph.infrastructure.YamlModulesYaml];
 * tests substitute a handwritten fake in `src/test`.
 *
 * A complete manifest must include every platform a `platform:<name>`
 * token refers to: [pathToPlatform] fails rather than inventing a path
 * for a missing entry.
 */
internal interface ModulesYaml {
    /**
     * Capability names listed under `planned:`.
     *
     * @return Names only (`jobs`), not Gradle paths. [GraphCheck.Planned] flags
     * any included `:modules:<name>:…` whose `<name>` is in this set.
     */
    fun planned(): Set<String>

    /**
     * Every `platform:` entry, including those with an empty `depends` list.
     *
     * @return All platforms the allow-list and the platform-graph check may mention.
     * `platform:*` expands to these paths.
     */
    fun platforms(): Collection<Platform>

    /**
     * Live capabilities under `modules:`, not `planned:`.
     *
     * @return Features whose layers must exist in Gradle and whose edges [GraphCheck.Features]
     * compares to the allow-list.
     */
    fun features(): Collection<Feature>

    /**
     * Allow-list tokens for a feature layer from the top-level `layers:` map.
     *
     * Tokens are the strings written in YAML (`own:domain`, `platform:*`,
     * `platform:kernel`, `any:contract`). Unknown tokens fail later, in
     * [GraphCheck.Features.Allow], not here.
     *
     * @param layer Layer name (`domain`, `application`, …), matching a key under `layers:`.
     * @return The token list, which may be empty if the layer exists and allows
     * nothing; `null` if [layer] is not a key under `layers:`. [GraphCheck.Features.Allow.allowed]
     * treats `null` as an error. [GraphCheck.Features.Allow.required] treats `null`
     * as no required edges.
     */
    fun tokens(layer: String): List<String>?

    /**
     * Gradle path of a declared platform.
     *
     * @param name Platform key, with or without a `platform:` prefix (`kernel`
     * and `platform:kernel` are the same lookup).
     * @return [Platform.path] of the matching [platforms] entry.
     * @throws IllegalStateException if no platform with that key is declared.
     * Feature allow-lists must not mention platforms omitted from `platform:`.
     */
    fun pathToPlatform(name: String): String {
        val key = name.removePrefix("platform:")
        return platforms().firstOrNull { platform -> platform.name == key }?.path()
            ?: error("Unknown platform '$key'")
    }
}
