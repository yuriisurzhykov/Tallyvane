package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider

internal fun unixPath(holder: KoPathProvider): String = holder.path.replace('\\', '/')

internal fun KoFileDeclaration.hasFrameworkImport(): Boolean =
    imports.any { import -> FRAMEWORK_IMPORT_PREFIXES.any { import.name.startsWith(it) } }

internal fun KoFileDeclaration.hasImportStartingWith(prefixes: Collection<String>): Boolean =
    imports.any { import -> prefixes.any { import.name.startsWith(it) } }

internal fun KoScope.applicationUseCases(): List<KoClassDeclaration> =
    classes(includeNested = false, includeLocal = false)
        .filter { it.resideInPackage("..application..") }
        .filterNot { it.resideInPackage("..port..") }
        .filter { klass ->
            USE_CASE_PREFIXES.any { prefix -> klass.name.startsWith(prefix) } ||
                klass.functions(includeNested = false, includeLocal = false).any { it.name == "invoke" }
        }

internal fun KoFileDeclaration.inLayer(layer: String): Boolean {
    val pkg = packagee?.name.orEmpty()
    return pkg == layer || pkg.endsWith(".$layer") || pkg.contains(".$layer.")
}

internal fun expectedPackage(path: String): String? {
    val normalised = path.replace('\\', '/')
    val platform = Regex("""/platform/([^/]+)/src/(?:main|test)/kotlin(?:/|$)""").find(normalised)
    val feature = Regex("""/modules/([^/]+)/([^/]+)/src/(?:main|test)/kotlin(?:/|$)""").find(normalised)
    val inApp = Regex("""/app/src/(?:main|test)/kotlin(?:/|$)""").containsMatchIn(normalised)
    return when {
        platform != null -> "tallyvane.platform.${platform.groupValues[1]}"
        feature != null -> "tallyvane.${feature.groupValues[1]}.${feature.groupValues[2]}"
        inApp -> "tallyvane.app"
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
