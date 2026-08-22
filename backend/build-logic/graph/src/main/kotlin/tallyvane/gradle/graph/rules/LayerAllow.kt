package tallyvane.gradle.graph.rules

import tallyvane.gradle.graph.manifest.FeatureManifest
import tallyvane.gradle.graph.manifest.ModuleManifest

internal class LayerAllow(
    private val manifest: ModuleManifest,
    private val module: String,
    private val feature: FeatureManifest,
) {
    fun allowed(layer: String): Set<String> {
        val tokens = manifest.layerAllow[layer] ?: error("Unknown layer '$layer' for module $module")
        return tokens.flatMap(::expand).toSet()
    }

    fun required(layer: String): Set<String> {
        val tokens = manifest.layerAllow[layer] ?: return emptySet()
        return tokens.flatMap(::requiredFrom).filter(::existsInThisBuild).toSet()
    }

    private fun requiredFrom(token: String): List<String> =
        when {
            token.startsWith("own:") -> expand(token)
            token == "any:contract" -> expand(token)
            token.startsWith("platform:") && token != "platform:*" -> expand(token)
            else -> emptyList()
        }

    private fun existsInThisBuild(path: String): Boolean {
        if (!path.startsWith(":modules:$module:")) {
            return true
        }
        val ownLayer = path.substringAfterLast(":")
        return ownLayer in feature.layers
    }

    private fun expand(token: String): List<String> =
        when {
            token == "platform:*" ->
                manifest.platformDepends.keys.map { GradleModulePath.platform(it).value }
            token.startsWith("platform:") ->
                listOf(GradleModulePath.platform(token.removePrefix("platform:")).value)
            token.startsWith("own:") -> ownLayer(token.removePrefix("own:"))
            token == "any:contract" ->
                feature.reads.map { GradleModulePath.feature(it, "contract").value }
            else -> error("Unknown layer allow token '$token'")
        }

    private fun ownLayer(layer: String): List<String> =
        if (layer in feature.layers) {
            listOf(GradleModulePath.feature(module, layer).value)
        } else {
            emptyList()
        }
}
