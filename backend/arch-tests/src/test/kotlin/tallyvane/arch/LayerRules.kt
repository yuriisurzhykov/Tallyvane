package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

internal fun domainIsPure(scope: KoScope): List<String> =
    scope.files
        .withoutException("domain-is-pure")
        .filter { it.inLayer("domain") }
        .filter { it.hasFrameworkImport() }
        .map { it.where() }

internal fun domainNoApplication(scope: KoScope): List<String> =
    scope.files
        .withoutException("domain-no-application")
        .filter { it.inLayer("domain") }
        .filter { file ->
            file.imports.any { import ->
                import.name.contains(".application.") ||
                    import.name.contains(".infrastructure.") ||
                    import.name.contains(".web.")
            }
        }.map { it.where() }

internal fun contractIsSelfContained(scope: KoScope): List<String> =
    scope.files
        .withoutException("contract-is-self-contained")
        .filter { it.inLayer("contract") }
        .filter { file ->
            val capability =
                file.packagee
                    ?.name
                    ?.removePrefix("tallyvane.")
                    ?.substringBefore('.')
                    .orEmpty()
            capability.isNotEmpty() &&
                file.imports.any { it.name.startsWith("tallyvane.$capability.domain") }
        }.map { it.where() }

internal fun contractIsImmutable(scope: KoScope): List<String> =
    scope
        .properties(includeNested = true)
        .withoutException("contract-is-immutable")
        .filter { it.resideInPackage("..contract..") }
        .filter { property ->
            property.isVar ||
                property.type?.name?.startsWith("Mutable") == true
        }.map { it.where() }

internal fun platformKnowsNoBusiness(scope: KoScope): List<String> =
    scope.files
        .withoutException("platform-knows-no-business")
        .filter { file -> file.packagee?.name?.startsWith("tallyvane.platform") == true }
        .filter { file ->
            file.imports.any { import ->
                import.name.startsWith("tallyvane.") &&
                    !import.name.startsWith("tallyvane.platform.")
            }
        }.map { it.where() }

internal fun webHasNoPersistence(scope: KoScope): List<String> =
    scope.files
        .withoutException("web-has-no-persistence")
        .filter { it.inLayer("web") }
        .filter { file ->
            file.hasImportStartingWith(
                listOf("org.jetbrains.exposed", "java.sql", "javax.sql"),
            )
        }.map { it.where() }

internal fun packageMatchesLayer(scope: KoScope): List<String> =
    scope.files
        .withoutException("package-matches-layer")
        .mapNotNull { file ->
            val expected = expectedPackage(unixPath(file)) ?: return@mapNotNull null
            val actual = file.packagee?.name.orEmpty()
            val ok = actual == expected || actual.startsWith("$expected.")
            if (ok) null else file.where()
        }

internal fun domainNoAnnotations(scope: KoScope): List<String> =
    scope
        .classesAndInterfacesAndObjects(includeNested = true)
        .withoutException("domain-no-annotations")
        .filter { it.resideInPackage("..domain..") }
        .filter { declaration -> declaration.annotations.any { isFrameworkAnnotation(it.name) } }
        .map { it.where() }

internal fun domainNoVar(scope: KoScope): List<String> =
    scope
        .properties(includeNested = true)
        .withoutException("domain-no-var")
        .filter { it.resideInPackage("..domain..") }
        .filter { it.isVar }
        .map { it.where() }

internal fun contractNoLogic(scope: KoScope): List<String> {
    val useCases =
        scope
            .classes(includeNested = false)
            .withoutException("contract-no-logic")
            .filter { it.resideInPackage("..contract..") }
            .filter { klass -> USE_CASE_PREFIXES.any { klass.name.startsWith(it) } }
            .map { it.where() }
    val nested =
        scope
            .classes(includeNested = true)
            .withoutException("contract-no-logic")
            .filter { it.resideInPackage("..contract..") }
            .filter { nestedClass ->
                val topLevel =
                    nestedClass.containingFile
                        .classes(includeNested = false, includeLocal = false)
                        .map { it.name } +
                        nestedClass.containingFile.interfaces(includeNested = false).map { it.name }
                nestedClass.name !in topLevel && nestedClass.name !in NESTED_IMPL_ALLOW
            }.map { it.where() }
    val framework =
        scope.files
            .withoutException("contract-no-logic")
            .filter { it.inLayer("contract") }
            .filter { it.hasFrameworkImport() }
            .map { it.where() }
    return (useCases + nested + framework).distinct()
}
