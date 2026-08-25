package tallyvane.gradle.graph.infrastructure

import org.yaml.snakeyaml.Yaml
import tallyvane.gradle.graph.domain.Feature
import tallyvane.gradle.graph.domain.ModulesYaml
import tallyvane.gradle.graph.domain.Platform
import java.io.File

/**
 * [ModulesYaml] parsed from a YAML document with SnakeYAML.
 *
 * This is the only type in the plugin that imports the parser. The document
 * must be a mapping with `layers` and `platform`; `modules` and `planned`
 * may be omitted (treated as no features / no planned names).
 *
 * @param text Entire YAML document, already read from disk or a fixture.
 * @param origin Label prefixed onto parse errors (`modules.yaml`, or a test
 * fixture name). Not opened as a file.
 * @throws IllegalStateException if [text] is empty, is not a mapping, or
 * is missing `layers` / `platform`, or if a nested value has the wrong shape
 * (see [YamlMapping]).
 */
internal class YamlModulesYaml(
    text: String,
    origin: String,
) : ModulesYaml {

    /**
     * Reads [file] as UTF-8 text and uses [File.getName] as [origin].
     *
     * @param file `modules.yaml` at the backend root, or any fixture with the same schema.
     */
    constructor(file: File) : this(file.readText(), file.name)

    private val plannedNames: Set<String>
    private val platformList: List<Platform>
    private val featureList: List<Feature>
    private val layerTokens: Map<String, List<String>>

    init {
        val loaded = Yaml().load<Any?>(text) ?: error("$origin is empty")
        val root = loaded as? Map<*, *> ?: error("$origin must be a mapping")
        val yaml = YamlMapping(root, origin)
        val live = readFeatures(yaml.optional("modules"))
        val plannedFeatures = readFeatures(yaml.optional("planned"))
        plannedNames = plannedFeatures.map { feature -> feature.name }.toSet()
        platformList = readPlatforms(yaml.required("platform"))
        featureList = live
        layerTokens = yaml.stringListMap("layers")
    }

    override fun planned(): Set<String> = plannedNames

    override fun platforms(): Collection<Platform> = platformList

    override fun features(): Collection<Feature> = featureList

    override fun tokens(layer: String): List<String>? = layerTokens[layer]

    /**
     * @param section `platform:` mapping; each child is a platform name
     *   whose `depends` list becomes [Platform.depends].
     */
    private fun readPlatforms(section: YamlMapping): List<Platform> =
        section.children().map { (name, spec) ->
            Platform(name, spec.stringList("depends"))
        }

    /**
     * @param section `modules:` or `planned:` mapping, or `null` when the
     *   key is absent.
     * @return One [Feature] per child; `layers` and `reads` default to
     *   empty lists when omitted.
     */
    private fun readFeatures(section: YamlMapping?): List<Feature> {
        if (section == null) {
            return emptyList()
        }
        return section.children().map { (name, spec) ->
            Feature(
                name = name,
                layers = spec.stringList("layers"),
                reads = spec.stringList("reads"),
            )
        }
    }
}
