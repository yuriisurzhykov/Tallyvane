package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

internal fun singlePublicMethod(scope: KoScope): List<String> =
    scope
        .applicationUseCases()
        .withoutException("single-public-method")
        .filter { klass ->
            val publicFunctions =
                klass
                    .functions(includeNested = false, includeLocal = false)
                    .filter { it.hasPublicOrDefaultModifier }
            publicFunctions.size != 1 || publicFunctions.single().name != "invoke"
        }.map { it.where() }

internal fun portIsInterface(scope: KoScope): List<String> =
    scope
        .classes(includeNested = false, includeLocal = false)
        .withoutException("port-is-interface")
        .filter { it.resideInPackage("..port..") }
        .map { it.where() }

internal fun adapterIsInternal(scope: KoScope): List<String> =
    scope
        .classes(includeNested = false, includeLocal = false)
        .withoutException("adapter-is-internal")
        .filter { it.resideInPackage("..infrastructure..") }
        .filterNot { it.name.endsWith("Factory") }
        .filterNot { it.hasInternalModifier }
        .map { it.where() }

internal fun noTopLevelFunctions(scope: KoScope): List<String> =
    scope.files
        .withoutException("no-top-level-functions")
        .filter { it.numFunctions(includeNested = false, includeLocal = false) > 0 }
        .map { it.where() }

internal fun noStatefulObjects(scope: KoScope): List<String> =
    scope
        .objects(includeNested = true)
        .withoutException("no-stateful-objects")
        .filterNot { it.hasCompanionModifier }
        .filter { obj ->
            obj.hasFunctions(includeNested = false, includeLocal = false) ||
                obj.properties(includeNested = true).any { it.isVar }
        }.map { it.where() }

internal fun noCompanionLogic(scope: KoScope): List<String> =
    scope
        .objects(includeNested = true)
        .withoutException("no-companion-logic")
        .filter { it.hasCompanionModifier }
        .filter { companion ->
            companion.functions(includeNested = false, includeLocal = false).any { function ->
                val body = function.text
                body.contains("if ") || body.contains("when ") || body.contains("when(")
            }
        }.map { it.where() }

internal fun oneTopLevelClass(scope: KoScope): List<String> =
    scope.files
        .withoutException("one-top-level-class")
        .filter { file ->
            val types =
                file.numClasses(includeNested = false, includeLocal = false) +
                    file.numInterfaces(includeNested = false) +
                    file.numObjects(includeNested = false)
            types > 1
        }.map { it.where() }

internal fun nestedImplIsPure(scope: KoScope): List<String> =
    scope
        .classes(includeNested = true, includeLocal = false)
        .withoutException("nested-impl-is-pure")
        .filter { it.name in NESTED_IMPL_ALLOW }
        .filter { it.containingFile.hasFrameworkImport() }
        .map { it.where() }

internal fun webOneUsecase(scope: KoScope): List<String> =
    scope
        .classes(includeNested = false, includeLocal = false)
        .withoutException("web-one-usecase")
        .filter { it.resideInPackage("..web..") }
        .filter { klass ->
            val useCaseParams =
                klass.primaryConstructor
                    ?.parameters
                    .orEmpty()
                    .count { parameter ->
                        val typeName = parameter.type.name
                        USE_CASE_PREFIXES.any { typeName.startsWith(it) }
                    }
            useCaseParams > 1
        }.map { it.where() }
