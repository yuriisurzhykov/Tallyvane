package tallyvane.gradle.graph

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
