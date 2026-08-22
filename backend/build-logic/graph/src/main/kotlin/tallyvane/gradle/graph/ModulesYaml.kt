package tallyvane.gradle.graph

import org.yaml.snakeyaml.Yaml

internal interface ModulesYaml {
    fun planned(): Set<String>

    fun platforms(): Collection<Platform>

    fun features(): Collection<Feature>

    fun tokens(layer: String): List<String>?

    fun pathToPlatform(name: String): String {
        val key = name.removePrefix("platform:")
        return platforms().firstOrNull { platform -> platform.name() == key }?.path()
            ?: error("Unknown platform '$key'")
    }

    class File(
        text: String,
        origin: String,
    ) : ModulesYaml {
        constructor(file: java.io.File) : this(file.readText(), file.name)

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
            plannedNames = plannedFeatures.map { feature -> feature.name() }.toSet()
            platformList = readPlatforms(yaml.required("platform"))
            featureList = live
            layerTokens = yaml.stringListMap("layers")
        }

        override fun planned(): Set<String> = plannedNames

        override fun platforms(): Collection<Platform> = platformList

        override fun features(): Collection<Feature> = featureList

        override fun tokens(layer: String): List<String>? = layerTokens[layer]

        private fun readPlatforms(section: YamlMapping): List<Platform> =
            section.children().map { (name, spec) ->
                Platform.FromManifest(name, spec.stringList("depends"))
            }

        private fun readFeatures(section: YamlMapping?): List<Feature> {
            if (section == null) {
                return emptyList()
            }
            return section.children().map { (name, spec) ->
                Feature.FromManifest(
                    name = name,
                    layers = spec.stringList("layers"),
                    reads = spec.stringList("reads"),
                )
            }
        }
    }
}

private class YamlMapping(
    private val values: Map<*, *>,
    private val origin: String,
) {
    fun required(key: String): YamlMapping = optional(key) ?: error("$origin is missing '$key'")

    fun optional(key: String): YamlMapping? {
        val value = values[key] ?: return null
        val nested = value as? Map<*, *> ?: error("$origin.$key must be a mapping")
        return YamlMapping(nested, "$origin.$key")
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

    fun children(): Map<String, YamlMapping> = keys().associateWith { key -> required(key) }
}
