package tallyvane.gradle.graph.domain

/**
 * A capability declared under `modules:` or `planned:` in `modules.yaml`.
 *
 * Graph checks use this to decide which Gradle projects belong to the
 * capability (`:modules:<name>:<layer>`), which of those layers exist in
 * this build, and which other capabilities' `contract` modules it may
 * depend on when a layer allow-list contains `any:contract`.
 *
 * Values only: the YAML adapter constructs this from a mapping. [path]
 * does not check [hasLayer] — callers that need a declared layer must
 * call [hasLayer] first (see [GraphCheck.Features.Allow]).
 *
 * @property name Capability key as written in YAML (`jobs`), never a Gradle
 * path (`:modules:jobs`) and never a layer suffix.
 * @property layers Layer names this capability declared (`domain`,
 * `application`, …), in YAML order. Duplicates are kept as written.
 * @property reads Capability names whose `contract` this one may read.
 * Empty means [readContractPaths] is empty.
 */
internal data class Feature(
    val name: String,
    val layers: List<String>,
    val reads: List<String>,
) {
    /**
     * Gradle project path of this capability's [layer].
     *
     * @param layer Layer name as in `modules.yaml` (`domain`), not a path and
     * not prefixed with `own:`.
     * @return `:modules:<name>:<layer>`, even when [layer] is not in [layers].
     */
    fun path(layer: String): String = ":modules:$name:$layer"

    /**
     * Whether this capability declared [layer] under `layers:`.
     *
     * @param layer Layer name as in YAML (`contract`, `domain`, …).
     */
    fun hasLayer(layer: String): Boolean = layer in layers

    /**
     * Gradle paths of the `contract` module of every capability in [reads].
     *
     * @return `:modules:<read>:contract` for each entry, in [reads] order.
     * Does not check that those capabilities exist in `modules.yaml`.
     */
    fun readContractPaths(): List<String> =
        reads.map { read -> ":modules:$read:contract" }

    /**
     * Whether [path] is a Gradle project of this capability.
     *
     * @param path Gradle path such as `:modules:jobs:domain`.
     * @return `true` iff [path] starts with `:modules:<name>:`.
     */
    fun isOwnModule(path: String): Boolean = path.startsWith(":modules:$name:")
}
