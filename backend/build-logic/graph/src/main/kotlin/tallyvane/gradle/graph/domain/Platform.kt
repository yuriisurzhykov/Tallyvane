package tallyvane.gradle.graph.domain

/**
 * A platform module declared under `platform:` in `modules.yaml`.
 *
 * [GraphCheck.Platforms] compares [path] and [expectedPaths] to the
 * included Gradle project of the same path. [GraphCheck.Features.Allow]
 * uses [path] when expanding `platform:*` and named `platform:` tokens.
 *
 * @property name Platform key as written in YAML (`kernel`), with or without a `platform:`
 * prefix on [depends] entries — [path] always strips that prefix.
 * @property depends Declared platform dependencies, each either `kernel`
 * or `platform:kernel`. Empty means no project dependencies.
 */
internal data class Platform(
    val name: String,
    val depends: List<String>,
) {
    /**
     * Gradle project path of this platform.
     *
     * @return `:platform:<name>` with any `platform:` prefix on [name]  removed, so `kernel`
     * and `platform:kernel` yield the same path.
     */
    fun path(): String = pathOf(name)

    /**
     * Gradle paths this platform is declared to depend on.
     *
     * @return [depends] mapped through the same `:platform:<name>` rule as [path],
     * in declaration order.
     */
    fun expectedPaths(): List<String> = depends.map(::pathOf)

    private fun pathOf(platformName: String): String =
        ":platform:${platformName.removePrefix("platform:")}"
}
