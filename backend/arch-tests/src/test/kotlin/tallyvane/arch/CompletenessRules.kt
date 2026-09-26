package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope
import java.io.File

internal fun portHasConformanceSuite(scope: KoScope): List<String> {
    val suiteNames =
        scope
            .classesAndInterfacesAndObjects(includeNested = true)
            .map { it.name }
            .filter { it.endsWith("Conformance") }
            .toSet()
    return scope
        .interfaces(includeNested = false)
        .withoutException("port-has-conformance-suite")
        .filter { it.resideInPackage("..port..") }
        .filter { port ->
            val implementations =
                scope
                    .classes(includeNested = true, includeLocal = false)
                    .count { klass -> klass.parentInterfaces().any { parent -> parent.name == port.name } }
            implementations > 1 && "${port.name}Conformance" !in suiteNames
        }.map { it.where() }
}

/**
 * The implementation is what holds the logic, so the implementation is what needs
 * the test — `SignInUseCase.SignIn` is covered by `SignInSpec`.
 */
internal fun usecaseHasTest(scope: KoScope): List<String> {
    val testNames = scope.classes(includeNested = false, includeLocal = false).map { it.name }.toSet()
    return scope
        .useCaseImplementations()
        .withoutException("usecase-has-test")
        .filter { klass ->
            klass.name + "Test" !in testNames && klass.name + "Spec" !in testNames
        }.map { it.where() }
}

/**
 * `docs/openapi.yaml` and the served endpoints describe the same API.
 *
 * §11.7 makes that file the source of truth: types for the frontend and for the extension are
 * generated from it, and CI gates backward compatibility on it. A source of truth that code can
 * quietly outgrow is a document, not a contract — and it had already been outgrown once, in slices
 * 11 and 12, before this rule existed.
 *
 * Both directions, because each catches a different mistake. An endpoint absent from the file ships
 * a contract nobody generated a client for. A path in the file that nothing serves promises a client
 * something that answers 404.
 *
 * What it cannot check without a running server: methods, statuses, and whether a body matches its
 * schema. That is slice 14's conformance run, which needs `app`. This is the half that works today.
 */
internal fun openapiCoversRoutes(scope: KoScope): List<String> {
    val files = scope.files.withoutException("openapi-covers-routes")
    val basePaths = files.mapNotNull { file ->
        val source = file.codeText()
        val packageName = PACKAGE.find(source)?.groupValues?.get(1) ?: return@mapNotNull null
        val basePath = BASE_PATH.find(source)?.groupValues?.get(1) ?: return@mapNotNull null
        packageName.substringBeforeLast('.') to basePath
    }
    val served = files.flatMap { file ->
        val source = file.codeText()
        val packageName = PACKAGE.find(source)?.groupValues?.get(1)
        val inheritedBase = basePaths
            .filter { (root, _) -> packageName == root || packageName?.startsWith("$root.") == true }
            .maxByOrNull { (root, _) -> root.length }
            ?.second
        servedBy(source, inheritedBase)
    }.toSet()
    val documented = documentedPaths()

    val undocumented = (served - documented).map { path -> "$path is served and absent from docs/openapi.yaml" }
    val unserved = (documented - served).map { path -> "docs/openapi.yaml describes $path, which nothing serves" }

    return undocumented + unserved
}

/**
 * Every full path one file registers: its base, plus the base joined with each literal sub-path.
 *
 * A first version collected bases only and compared by prefix, which passed with `/health/ready`
 * deleted from the spec — the base was still documented, so the missing sub-path was invisible.
 * That is the common mistake, not the rare one: a module that ships with no spec entry at all is
 * hard to miss in review, an endpoint added to an existing module is not.
 *
 * The base alone is included because a handler registered at the base takes no literal
 * (`route.get { … }`). A sub-path spelled with a Ktor parameter reads the same as OpenAPI's —
 * `/{id}` — so the two sides compare directly. A path built at runtime would be invisible here, and
 * there is none; if one appears, this rule will not notice it, which is why slice 14's conformance
 * run against a live server is still owed.
 */
private fun servedBy(source: String, inheritedBase: String? = null): Set<String> {
    val base = BASE_PATH.find(source)?.groupValues?.get(1) ?: inheritedBase ?: return emptySet()
    val nested = ROUTE.findAll(source)
        .map { found -> found.groupValues[2] }
        .filter { sub -> sub.startsWith("/") }
        .map { sub -> base + sub }
    return nested.toSet() + base
}

/**
 * Top-level keys under `paths:`, read by shape rather than by a YAML parser.
 *
 * `arch-tests` has no YAML library and taking one for four lines is worse than the regex: the file
 * is authored here, its `paths:` entries are two-space indented, and a nested key that happened to
 * start with `/` would be indented further. Stated so the limitation is known rather than
 * discovered.
 */
private fun documentedPaths(): Set<String> {
    val spec = File(repoRoot(), "docs/openapi.yaml")
    check(spec.isFile) { "§11.7 requires ${spec.invariantSeparatorsPath} to exist" }
    return SPEC_PATH.findAll(spec.readText()).map { found -> found.groupValues[1] }.toSet()
}

private val BASE_PATH = Regex("""BasePath\("([^"]+)"\)""")
private val PACKAGE = Regex("""(?m)^package\s+([A-Za-z0-9_.]+)""")

private val ROUTE = Regex("""\b(get|post|put|patch|delete)\(\s*"([^"]*)"""")

private val SPEC_PATH = Regex("""^ {2}(/[A-Za-z0-9\-_/{}]*):\s*$""", RegexOption.MULTILINE)

internal fun registryOwnsBranching(scope: KoScope): List<String> {
    val kinds = listOf("JobSourceKind", "ChannelKind", "ReminderCode")
    return scope.files
        .withoutException("registry-owns-branching")
        .filterNot { file -> file.inLayer("registry") }
        .filter { file ->
            val code = file.codeText()
            code.contains("when") && kinds.any { kind -> code.contains(kind) }
        }.map { it.where() }
}

/**
 * The identity HTTP adapter is an interface boundary, including its small helpers.
 * Data-only contracts have no declared behavior and are deliberately invisible to this rule.
 * Each concrete type with a public method must implement its own interface and live inside it;
 * the nested implementation name is one plain word (for example `Default`).
 */
internal fun identityWebBehaviorIsInterface(scope: KoScope): List<String> {
    val identityClasses = scope
        .classes(includeNested = true, includeLocal = false)
        .filter { it.resideInPackage("tallyvane.identity.web..") }
        .filter { klass ->
            klass.functions(includeNested = false, includeLocal = false).any { function ->
                val declaration = function.text.substringBefore("{").substringBefore("=")
                !Regex("\\b(private|internal|protected)\\b").containsMatchIn(declaration)
            }
        }
    val classesByInterface = identityClasses
        .flatMap { klass -> klass.parentInterfaces().map { it.name.substringBefore('<') to klass } }
        .groupBy({ it.first }, { it.second })
    return identityClasses.flatMap { klass ->
        val interfaces = klass.parentInterfaces().map { it.name.substringBefore('<') }.toSet()
        when {
            interfaces.isEmpty() -> listOf(klass.where())
            else ->
                interfaces
                    .filter { interfaceName -> classesByInterface[interfaceName].orEmpty().size == 1 }
                    .filterNot { interfaceName ->
                        klass.name.matches(Regex("[A-Z][A-Za-z0-9]*")) &&
                            klass.isNestedInOwnUseCaseInterface(setOf(interfaceName))
                    }
                    .map { klass.where() }
        }
    }
}
