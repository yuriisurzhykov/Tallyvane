package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

internal fun noBannedSuffix(scope: KoScope): List<String> = scope
    .classesAndInterfacesAndObjects(includeNested = true)
    .withoutException("no-banned-suffix")
    .filter { declaration -> BANNED_SUFFIXES.any { declaration.name.endsWith(it) } }
    .map { it.where() }

internal fun portNaming(scope: KoScope): List<String> = scope
    .interfaces(includeNested = false)
    .withoutException("port-naming")
    .filter { it.resideInPackage("..port..") }
    .filter { iface ->
        iface.name.endsWith("Interface") ||
            (iface.name.length > 1 && iface.name.startsWith("I") && iface.name[1].isUpperCase())
    }.map { it.where() }

internal fun noImplSuffix(scope: KoScope): List<String> = scope
    .classesAndInterfacesAndObjects(includeNested = true)
    .withoutException("no-impl-suffix")
    .filter { it.name.endsWith("Impl") }
    .map { it.where() }

internal fun adapterNamedByMechanism(scope: KoScope): List<String> = scope
    .classes(includeNested = false)
    .withoutException("adapter-named-by-mechanism")
    .filter { it.resideInPackage("..infrastructure..") }
    .filter { it.name.endsWith("Impl") || it.namedLikeFake() }
    .map { it.where() }

internal fun noFakeInMain(scope: KoScope): List<String> = scope
    .classesAndInterfacesAndObjects(includeNested = true)
    .withoutException("no-fake-in-main")
    .filter { it.namedLikeFake() }
    .map { it.where() }

internal fun usecaseIsImperative(scope: KoScope): List<String> = scope
    .applicationUseCases()
    .withoutException("usecase-is-imperative")
    .filter { klass -> USE_CASE_PREFIXES.none { klass.name.startsWith(it) } }
    .map { it.where() }

internal fun appHasNoLogic(scope: KoScope): List<String> = scope
    .classesAndInterfacesAndObjects(includeNested = false)
    .withoutException("app-has-no-logic")
    .filter { declaration ->
        unixPath(declaration.containingFile).contains("/app/src/") ||
            declaration.resideInPackage("tallyvane.app..")
    }.filter { declaration ->
        !declaration.name.endsWith("Wiring") &&
            !declaration.name.endsWith("Configuration") &&
            !declaration.name.endsWith("Application")
    }.map { it.where() }

internal fun noHardcodedProductName(scope: KoScope): List<String> = scope.files
    .withoutException("no-hardcoded-product-name")
    .filter { file ->
        val allowed = unixPath(file).contains("/strings/") || unixPath(file).contains("/config/")
        !allowed && Regex("""\b$PRODUCT_NAME\b""").containsMatchIn(file.codeText())
    }.map { it.where() }
