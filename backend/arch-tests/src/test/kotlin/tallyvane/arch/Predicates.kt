package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider

internal fun unixPath(holder: KoPathProvider): String = holder.path.replace('\\', '/')

internal fun KoFileDeclaration.hasFrameworkImport(): Boolean =
    imports.any { import -> FRAMEWORK_IMPORT_PREFIXES.any { import.name.startsWith(it) } }

internal fun KoFileDeclaration.hasImportStartingWith(prefixes: Collection<String>): Boolean =
    imports.any { import -> prefixes.any { import.name.startsWith(it) } }

/**
 * The use-case interfaces in scope, found by the marker rather than guessed at.
 *
 * This used to match a list of twenty-eight imperative prefixes or a function
 * named `invoke`. The list rejected `SignIn`, `Upload`, `Open` and `Delete` —
 * contradicting what a use case is — and accepted `SaveThing`, so it was wrong in
 * both directions. `UseCase` answers the question exactly (ADR-053).
 */
internal fun KoScope.useCaseInterfaces(): List<KoInterfaceDeclaration> = interfaces(includeNested = true)
    .filter { declaration -> declaration.parentInterfaces().any { it.name == USE_CASE_MARKER } }

/**
 * The classes implementing a use case, which is where the logic and the test are.
 *
 * Nested, because the implementation sits inside its interface; the previous
 * predicate passed `includeNested = false` and would have found none of them.
 */
internal fun KoScope.useCaseImplementations(): List<KoClassDeclaration> {
    val markers = useCaseInterfaces().map { it.name }.toSet() + USE_CASE_MARKER
    return classes(includeNested = true, includeLocal = false)
        .filter { klass -> klass.parentInterfaces().any { it.name in markers } }
}

/**
 * True when this class or object is nested inside the use-case interface it
 * implements. Top-level types, and types nested in an unrelated class or in a
 * different use-case interface, are the shapes [usecaseIsInterface] rejects.
 */
internal fun KoClassAndObjectDeclaration.isNestedInOwnUseCaseInterface(markers: Set<String>): Boolean {
    val container = containingDeclaration
    return container is KoInterfaceDeclaration &&
        container.name in markers &&
        container.name in parentInterfaces().map { it.name }
}

internal fun KoFileDeclaration.inLayer(layer: String): Boolean {
    val pkg = packagee?.name.orEmpty()
    return pkg == layer || pkg.endsWith(".$layer") || pkg.contains(".$layer.")
}

internal fun expectedPackage(path: String): String? {
    val normalised = path.replace('\\', '/')
    val platform = Regex("""/platform/([^/]+)/src/(?:main|test)/kotlin(?:/|$)""").find(normalised)
    val feature = Regex("""/modules/([^/]+)/([^/]+)/src/(?:main|test)/kotlin(?:/|$)""").find(normalised)
    val inServer = Regex("""/server/src/(?:main|test)/kotlin(?:/|$)""").containsMatchIn(normalised)
    return when {
        platform != null -> "tallyvane.platform.${platform.groupValues[1]}"
        feature != null -> "tallyvane.${feature.groupValues[1]}.${feature.groupValues[2]}"
        inServer -> "tallyvane.server"
        else -> null
    }
}

internal fun moduleNameFromPath(path: String): String? =
    Regex("""/modules/([^/]+)/""").find(path.replace('\\', '/'))?.groupValues?.get(1)

internal fun KoFileDeclaration.implementsSimpleName(simpleName: String): Boolean {
    val nestedClasses = classes(includeNested = true, includeLocal = false)
    return nestedClasses.any { klass ->
        klass.name == simpleName ||
            klass.parentInterfaces().any { parent -> parent.name == simpleName }
    }
}

internal fun KoNameProvider.namedLikeFake(): Boolean =
    name == "Fake" || name.startsWith("Fake") || name.endsWith("Fake")

internal fun isFrameworkAnnotation(name: String): Boolean = name == "Serializable" ||
    name == "Entity" ||
    name == "Table" ||
    FRAMEWORK_IMPORT_PREFIXES.any { name.startsWith(it) }
