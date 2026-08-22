package tallyvane.gradle.graph

internal interface GraphCheck {
    fun findings(
        yaml: ModulesYaml,
        projects: IncludedProjects,
    ): List<Finding>

    object Planned : GraphCheck {
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> =
            projects.paths().mapNotNull { path ->
                finding(yaml.planned(), path)
            }

        private fun finding(
            planned: Set<String>,
            path: String,
        ): Finding? {
            val module = path.removePrefix(":modules:").substringBefore(":")
            val includedPlan = path.startsWith(":modules:") && module in planned
            return if (includedPlan) {
                Finding(
                    "Planned module $module is included as $path — " +
                        "move it from planned: to modules: in modules.yaml",
                )
            } else {
                null
            }
        }
    }

    object Platforms : GraphCheck {
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> =
            yaml.platforms().flatMap { platform ->
                Node(platform).findings(projects)
            }

        private class Node(
            private val platform: Platform,
        ) {
            fun findings(projects: IncludedProjects): List<Finding> {
                val path = platform.path()
                val expected = platform.expectedPaths().sorted()
                val got = projects.dependencies(path).sorted()
                return when {
                    path !in projects.paths() ->
                        listOf(
                            Finding(
                                "modules.yaml platform.${platform.name()} is missing " +
                                    "from the Gradle graph ($path)",
                            ),
                        )

                    got == expected -> emptyList()
                    else -> listOf(mismatch(path, expected, got))
                }
            }

            private fun mismatch(
                path: String,
                expected: List<String>,
                got: List<String>,
            ): Finding {
                val extra = got - expected.toSet()
                val missing = expected - got.toSet()
                val extraText = if (extra.isEmpty()) "" else " Extra: $extra."
                val missingText = if (missing.isEmpty()) "" else " Missing: $missing."
                return Finding("Platform graph mismatch for $path.$extraText$missingText")
            }
        }
    }

    object Features : GraphCheck {
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> =
            yaml.features().flatMap { feature ->
                feature.layerNames().flatMap { layer ->
                    Layer(yaml, feature, layer).findings(projects)
                }
            }

        internal class Allow(
            private val yaml: ModulesYaml,
            private val feature: Feature,
        ) {
            fun allowed(layer: String): Set<String> {
                val tokens =
                    yaml.tokens(layer)
                        ?: error("Unknown layer '$layer' for module ${feature.name()}")
                return tokens.flatMap(::expand).toSet()
            }

            fun required(layer: String): Set<String> {
                val tokens = yaml.tokens(layer) ?: return emptySet()
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
                if (!feature.isOwnModule(path)) {
                    return true
                }
                return feature.hasLayer(path.substringAfterLast(":"))
            }

            private fun expand(token: String): List<String> =
                when {
                    token == "platform:*" -> yaml.platforms().map { platform -> platform.path() }
                    token.startsWith("platform:") ->
                        listOf(yaml.pathToPlatform(token.removePrefix("platform:")))

                    token.startsWith("own:") -> ownLayer(token.removePrefix("own:"))
                    token == "any:contract" -> feature.readContractPaths()
                    else -> error("Unknown layer allow token '$token'")
                }

            private fun ownLayer(layer: String): List<String> =
                if (feature.hasLayer(layer)) {
                    listOf(feature.path(layer))
                } else {
                    emptyList()
                }
        }

        private class Layer(
            yaml: ModulesYaml,
            private val feature: Feature,
            private val layer: String,
        ) {
            private val allow = Allow(yaml, feature)
            private val path = feature.path(layer)

            fun findings(projects: IncludedProjects): List<Finding> {
                if (path !in projects.paths()) {
                    return listOf(
                        Finding(
                            "modules.yaml modules.${feature.name()} layer $layer " +
                                "is missing from the Gradle graph ($path)",
                        ),
                    )
                }
                val allowed = allow.allowed(layer)
                val got = projects.dependencies(path)
                val extra = (got - allowed).map { edge -> Finding("Undeclared dependency: $path -> $edge") }
                val missing =
                    (allow.required(layer) - got).map { edge ->
                        Finding("Declared but unused dependency: $path -> $edge")
                    }
                return extra + missing
            }
        }
    }

    class Banned(
        private val groups: List<String> =
            listOf(
                "io.mockk",
                "org.mockito",
                "org.mockito.kotlin",
            ),
    ) : GraphCheck {
        @Suppress("UNUSED_PARAMETER")
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> = of(projects.coordinates())

        private fun of(coordinates: List<IncludedProjects.Coordinate>): List<Finding> =
            coordinates.mapNotNull { coordinate ->
                if (isBanned(coordinate.group())) {
                    Finding("Banned coordinate ${coordinate.group()} in ${coordinate.projectPath()}")
                } else {
                    null
                }
            }

        private fun isBanned(group: String): Boolean =
            groups.any { banned -> group == banned || group.startsWith("$banned.") }
    }
}
