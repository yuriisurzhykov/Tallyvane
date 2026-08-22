package tallyvane.gradle.graph.manifest

import org.yaml.snakeyaml.Yaml
import java.io.File

internal class YamlManifestSource(
    private val text: String,
    private val origin: String,
) {
    constructor(file: File) : this(file.readText(), file.name)

    fun load(): ModuleManifest {
        val loaded = Yaml().load<Any?>(text) ?: error("$origin is empty")
        val root = loaded as? Map<*, *> ?: error("$origin must be a mapping")
        val yaml = YamlMap(root, origin)
        val modules = features(yaml.optional("modules"))
        val planned = features(yaml.optional("planned"))
        return ModuleManifest(
            layerAllow = yaml.stringListMap("layers"),
            platformDepends = platformDepends(yaml.required("platform")),
            modules = modules,
            planned = planned.keys,
        )
    }

    private fun platformDepends(platform: YamlMap): Map<String, List<String>> =
        platform.children().mapValues { (_, spec) -> spec.stringList("depends") }

    private fun features(section: YamlMap?): Map<String, FeatureManifest> {
        if (section == null) {
            return emptyMap()
        }
        return section.children().mapValues { (_, spec) ->
            FeatureManifest(
                layers = spec.stringList("layers"),
                reads = spec.stringList("reads"),
            )
        }
    }
}
