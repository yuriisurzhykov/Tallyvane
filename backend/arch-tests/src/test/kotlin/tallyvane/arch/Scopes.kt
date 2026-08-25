package tallyvane.arch

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import java.io.File

internal fun konsistRoot(): File = File(System.getProperty("konsist.root") ?: error("konsist.root is not set"))

internal fun repoRoot(): File = File(System.getProperty("repo.root") ?: error("repo.root is not set"))

internal fun productionScope(): KoScope = scopeFromKotlinDirs(listOf("main"))

/**
 * Everything a rule about code-in-general must see, `testFixtures` included.
 *
 * Leaving that source set out would open two holes the moment a fake is shared:
 * `no-mock-libraries` would stop looking exactly where a shared double lives, and
 * `port-has-conformance-suite` would report a suite as missing because it had been
 * moved somewhere the scope did not reach.
 */
internal fun codeScope(): KoScope = scopeFromKotlinDirs(listOf("main", "test", "testFixtures"))

internal fun fixtureScope(rule: String): KoScope {
    val dir = File(konsistRoot(), "arch-tests/src/test/resources/konsist-fixtures/$rule")
    check(dir.isDirectory) { "Missing fixture directory ${dir.absolutePath}" }
    return Konsist.scopeFromDirectory(relativeToRoot(dir))
}

private fun scopeFromKotlinDirs(sourceSets: List<String>): KoScope {
    val dirs = kotlinDirs(konsistRoot(), sourceSets)
    check(dirs.isNotEmpty()) { "No Kotlin directories under ${konsistRoot().absolutePath}" }
    return dirs.map { Konsist.scopeFromDirectory(relativeToRoot(it)) }.reduce { a, b -> a + b }
}

private fun relativeToRoot(dir: File): String = dir.relativeTo(konsistRoot()).invariantSeparatorsPath

private fun kotlinDirs(root: File, sourceSets: List<String>): List<File> {
    val markers = sourceSets.map { set -> "/src/$set/kotlin" }
    return listOf("platform", "app", "modules")
        .map { File(root, it) }
        .filter { it.isDirectory }
        .flatMap { top ->
            top
                .walkTopDown()
                .filter { it.isDirectory }
                .filter { dir ->
                    val path = dir.invariantSeparatorsPath
                    markers.any { path.endsWith(it) }
                }.filter { dir -> dir.walkTopDown().any { it.isFile && it.extension == "kt" } }
                .toList()
        }
}
