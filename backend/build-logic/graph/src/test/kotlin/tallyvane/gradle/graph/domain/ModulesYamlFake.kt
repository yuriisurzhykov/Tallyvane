package tallyvane.gradle.graph.domain

/**
 * [ModulesYaml] whose collections are passed in, for Kotest specs.
 *
 * Lives in `src/test` so it does not ship in the plugin jar (ADR-044).
 * [pathToPlatform] is the default on the port: tests that expand
 * `platform:<name>` must put that platform in [platforms].
 *
 * @param planned Names under `planned:`; default none.
 * @param platforms Declared platforms; default none.
 * @param features Live `modules:` capabilities; default none.
 * @param layerAllow `layers:` map from layer name to tokens. [tokens]
 *   returns `null` for a layer that is not a key.
 */
internal class ModulesYamlFake(
    private val planned: Set<String> = emptySet(),
    private val platforms: Collection<Platform> = emptyList(),
    private val features: Collection<Feature> = emptyList(),
    private val layerAllow: Map<String, List<String>> = emptyMap(),
) : ModulesYaml {
    override fun planned(): Set<String> = planned

    override fun platforms(): Collection<Platform> = platforms

    override fun features(): Collection<Feature> = features

    override fun tokens(layer: String): List<String>? = layerAllow[layer]
}
