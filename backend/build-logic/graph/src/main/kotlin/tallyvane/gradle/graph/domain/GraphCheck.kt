package tallyvane.gradle.graph.domain

/**
 * One comparison of `modules.yaml` against the included Gradle graph.
 *
 * Each nested type is a distinct check with the same [findings] signature so
 * [tallyvane.gradle.graph.application.ValidateModuleGraph] can compose them
 * as a list. Adding a check is a new nested type plus an entry on that
 * list, not a new branch inside an existing check.
 *
 * [findings] returns only this check's disagreements. An empty list means
 * this check is clean, not that the whole graph is.
 */
internal interface GraphCheck {
    /**
     * Disagreements this check finds between the declared graph and the
     * included build.
     *
     * @param yaml Declared planned names, platforms, features, and layer
     *   tokens. Some checks ignore parts of it ([Banned] ignores it
     *   entirely so it can share this signature).
     * @param projects Leaf paths, compile project-dependencies, and
     *   external coordinates of the build under test.
     * @return Findings this check owns, in the order it discovers them.
     *   Empty means this check is clean.
     */
    fun findings(
        yaml: ModulesYaml,
        projects: IncludedProjects,
    ): List<Finding>

    /**
     * Fails when a capability still listed under `planned:` is already
     * included as a Gradle project under `:modules:`.
     *
     * The remedy is to move the entry from `planned:` to `modules:` in
     * `modules.yaml`. Paths that are not under `:modules:` are ignored.
     */
    object Planned : GraphCheck {
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> =
            projects.paths().mapNotNull { path ->
                finding(yaml.planned(), path)
            }

        /**
         * @param planned Capability names from `planned:`.
         * @param path One included Gradle path.
         * @return A finding if [path] is `:modules:<name>:…` and `<name>`
         * is in [planned]; otherwise `null`.
         */
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

    /**
     * Fails when a `platform:` row is missing from Gradle, or when its
     * compile project-dependencies are not exactly the declared `depends`
     * list.
     *
     * Extra edges and missing edges are reported on the same finding.
     * Named `Platforms` so it does not clash with [Platform].
     */
    object Platforms : GraphCheck {
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> =
            yaml.platforms().flatMap { platform ->
                Node(platform).findings(projects)
            }

        /**
         * One `platform:` row compared to the Gradle project of the same path.
         *
         * @param platform Declared platform; [Platform.path] is the Gradle
         *   project this node looks up.
         */
        private class Node(
            private val platform: Platform,
        ) {
            /**
             * @param projects Included build to compare against.
             * @return One finding if the project is missing or the edge
             *   sets differ; empty if they match.
             */
            fun findings(projects: IncludedProjects): List<Finding> {
                val path = platform.path()
                val expected = platform.expectedPaths().sorted()
                val got = projects.dependencies(path).sorted()
                return when {
                    path !in projects.paths() ->
                        listOf(
                            Finding(
                                "modules.yaml platform.${platform.name} is missing " +
                                    "from the Gradle graph ($path)",
                            ),
                        )

                    got == expected -> emptyList()
                    else -> listOf(mismatch(path, expected, got))
                }
            }

            /**
             * @param path Gradle path of the platform project.
             * @param expected Sorted declared dependency paths.
             * @param got Sorted actual compile project-dependency paths.
             */
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

    /**
     * Fails when a declared feature layer is missing from Gradle, when a
     * compile project-dependency is not in that layer's allow-list, or when
     * a required allow-list edge is unused.
     *
     * Required edges are `own:<layer>`, `any:contract`, and named
     * `platform:<name>` — not `platform:*`, so a module that does not use
     * storage is not forced to depend on it.
     */
    object Features : GraphCheck {
        override fun findings(
            yaml: ModulesYaml,
            projects: IncludedProjects,
        ): List<Finding> =
            yaml.features().flatMap { feature ->
                feature.layers.flatMap { layer ->
                    Layer(yaml, feature, layer).findings(projects)
                }
            }

        /**
         * Turns `layers:` tokens for one [feature] into Gradle paths.
         *
         * Token grammar:
         * - `platform:*` — every [ModulesYaml.platforms] path; allowed, not required.
         * - `platform:<name>` — [ModulesYaml.pathToPlatform]; required if present.
         * - `own:<layer>` — [Feature.path] when [Feature.hasLayer], else nothing.
         * - `any:contract` — [Feature.readContractPaths]; required.
         *
         * @param yaml Manifest that supplies layer tokens and platforms.
         * @param feature Capability whose own layers and `reads` are expanded.
         */
        internal class Allow(
            private val yaml: ModulesYaml,
            private val feature: Feature,
        ) {
            /**
             * Gradle paths [layer] may depend on, including optional wildcards.
             *
             * @param layer Feature layer whose `layers:` tokens are expanded
             *   (`domain`, `application`, …).
             * @return Union of expanded tokens. Order is not significant.
             * @throws IllegalStateException if [ModulesYaml.tokens] is `null`
             *   for [layer] (unknown layer name for [feature]).
             */
            fun allowed(layer: String): Set<String> {
                val tokens =
                    yaml.tokens(layer)
                        ?: error("Unknown layer '$layer' for module ${feature.name}")
                return tokens.flatMap(::expand).toSet()
            }

            /**
             * Gradle paths [layer] must depend on, if those projects exist
             * in this capability.
             *
             * @param layer Feature layer to read tokens for.
             * @return Required subset of [allowed]: `own:`, `any:contract`,
             *   and named `platform:` tokens, filtered by [existsInThisBuild].
             *   Unknown [layer] yields an empty set rather than an error, so
             *   a missing-layer finding can be reported by [Layer] first.
             */
            fun required(layer: String): Set<String> {
                val tokens = yaml.tokens(layer) ?: return emptySet()
                return tokens.flatMap(::requiredFrom).filter(::existsInThisBuild).toSet()
            }

            /**
             * @param token One `layers:` entry.
             * @return [expand] for required token shapes; empty for
             *   `platform:*` and unknown shapes that [expand] would reject
             *   only when they appear in [allowed].
             */
            private fun requiredFrom(token: String): List<String> =
                when {
                    token.startsWith("own:") -> expand(token)
                    token == "any:contract" -> expand(token)
                    token.startsWith("platform:") && token != "platform:*" -> expand(token)
                    else -> emptyList()
                }

            /**
             * Whether a required path should be enforced for [feature].
             *
             * @param path Candidate Gradle path from [expand].
             * @return `true` for paths outside this capability. For
             *   `:modules:<feature>:…`, `true` only if that layer was
             *   declared, so `own:contract` is not required when the
             *   capability has no `contract` layer.
             */
            private fun existsInThisBuild(path: String): Boolean {
                if (!feature.isOwnModule(path)) {
                    return true
                }
                return feature.hasLayer(path.substringAfterLast(":"))
            }

            /**
             * @param token One `layers:` entry, as written in YAML.
             * @return Gradle paths that token names.
             * @throws IllegalStateException if [token] is not one of
             *   `platform:*`, `platform:<name>`, `own:<layer>`,
             *   `any:contract`, or if `platform:<name>` is undeclared.
             */
            private fun expand(token: String): List<String> =
                when {
                    token == "platform:*" -> yaml.platforms().map { platform -> platform.path() }
                    token.startsWith("platform:") ->
                        listOf(yaml.pathToPlatform(token.removePrefix("platform:")))

                    token.startsWith("own:") -> ownLayer(token.removePrefix("own:"))
                    token == "any:contract" -> feature.readContractPaths()
                    else -> error("Unknown layer allow token '$token'")
                }

            /**
             * @param layer Layer suffix of an `own:` token.
             * @return [Feature.path] in a single-element list when declared;
             *   empty if this capability has no such layer.
             */
            private fun ownLayer(layer: String): List<String> =
                if (feature.hasLayer(layer)) {
                    listOf(feature.path(layer))
                } else {
                    emptyList()
                }
        }

        /**
         * One feature layer compared to one Gradle project.
         *
         * @param yaml Manifest for [Allow].
         * @param feature Capability that declared [layer].
         * @param layer Layer name (`domain`); [Feature.path] is the Gradle
         *   project this comparison uses.
         */
        private class Layer(
            yaml: ModulesYaml,
            private val feature: Feature,
            private val layer: String,
        ) {
            private val allow = Allow(yaml, feature)
            private val path = feature.path(layer)

            /**
             * @param projects Included build to compare against.
             * @return Missing-project finding, undeclared-edge findings,
             *   and declared-but-unused findings, in that order.
             */
            fun findings(projects: IncludedProjects): List<Finding> {
                if (path !in projects.paths()) {
                    return listOf(
                        Finding(
                            "modules.yaml modules.${feature.name} layer $layer " +
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

    /**
     * Fails when a leaf declares MockK or Mockito on any configuration,
     * including test.
     *
     * A group matches if it equals a banned group or starts with
     * `banned.` (`org.mockito.kotlin` matches `org.mockito`).
     *
     * @param groups Maven groups to reject. Production uses `io.mockk`,
     * `org.mockito`, and `org.mockito.kotlin`. Tests pass a different
     * list when they need to name a group without editing this default.
     */
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

        /**
         * @param coordinates External modules of every leaf.
         * @return One finding per banned [IncludedProjects.Coordinate.group],
         * in [coordinates] order.
         */
        private fun of(coordinates: List<IncludedProjects.Coordinate>): List<Finding> =
            coordinates.mapNotNull { coordinate ->
                if (isBanned(coordinate.group)) {
                    Finding("Banned coordinate ${coordinate.group} in ${coordinate.projectPath}")
                } else {
                    null
                }
            }

        /**
         * @param group Maven group of one external dependency.
         * @return `true` if [group] is banned or is a nested group of a
         * banned prefix.
         */
        private fun isBanned(group: String): Boolean =
            groups.any { banned -> group == banned || group.startsWith("$banned.") }
    }
}
