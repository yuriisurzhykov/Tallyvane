package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

/**
 * A use case publishes one action, so its interface declares one method.
 *
 * The method used to have to be named `invoke`. It is now forbidden to be: an
 * `operator` call takes its whole meaning from the field name the consumer picked,
 * so `a(request)` and `b(request)` read alike under review even when they are
 * unrelated actions. The name belongs to the type, where nobody downstream can
 * change it (ADR-053).
 */
internal fun singlePublicMethod(scope: KoScope): List<String> = scope
    .useCaseInterfaces()
    .withoutException("single-public-method")
    .filter { declaration ->
        // Every function counts, not only the public ones. Kotlin does allow a
        // `private` interface member that has a body — measured, not assumed — and
        // its only purpose is to share code between default implementations in the
        // same interface. A use-case interface has none of those, so such a helper
        // is either dead or logic that crept into the abstraction.
        //
        // The body check is the same argument reaching the one declared method: a
        // default implementation would let the nested class skip the override, and
        // nothing else would notice.
        val declared = declaration.functions(includeNested = false).singleOrNull()
        declared == null || declared.name == "invoke" || declared.hasBlockBody || declared.hasExpressionBody
    }.map { it.where() }

/**
 * A use case is published as an interface; the implementation nests inside it.
 *
 * Consumers receive the abstraction and the composition root chooses the concrete
 * type, which is the reason a top-level class implementing [USE_CASE_MARKER] is a
 * violation rather than a shortcut.
 */
internal fun usecaseIsInterface(scope: KoScope): List<String> {
    // The marker is usually reached through the use-case interface rather than
    // directly, so matching only `UseCase` caught the one shape nobody writes and
    // missed `class SignIn : SignInUseCase` sitting beside its interface. And a
    // declaration kind the predicate does not ask for is a way round on its own:
    // `classes()` never returns an object. Both were holes until a test looked.
    val markers = scope.useCaseInterfaces().map { it.name }.toSet() + USE_CASE_MARKER
    val topLevelClasses =
        scope
            .classes(includeNested = false, includeLocal = false)
            .withoutException("usecase-is-interface")
            .filter { klass -> klass.parentInterfaces().any { it.name in markers } }
            .map { it.where() }
    val topLevelObjects =
        scope
            .objects(includeNested = false)
            .withoutException("usecase-is-interface")
            .filter { declaration -> declaration.parentInterfaces().any { it.name in markers } }
            .map { it.where() }
    return topLevelClasses + topLevelObjects
}

/**
 * `Verdict` is a directive to one transaction, not a result anything hands back.
 *
 * Left unchecked it would spread: a port returns it, then a contract carries it,
 * and the codebase has the second competing result type ENGINEERING-PRINCIPLES.md
 * rejects. Confined to the last expression of a transactional block it stays what
 * ADR-052 says it is. Parameters are untouched — `inTransaction` takes one, which
 * is the whole point.
 */
internal fun noVerdictInSignature(scope: KoScope): List<String> {
    val returned =
        scope
            .functions(includeNested = true, includeLocal = false)
            .withoutException("no-verdict-in-signature")
            .filter { function -> function.returnType?.name?.namesVerdict() == true }
            .map { it.where() }
    val held =
        scope
            .properties(includeNested = true)
            .withoutException("no-verdict-in-signature")
            .filter { property -> property.type?.name?.namesVerdict() == true }
            .map { it.where() }
    return (returned + held).distinct()
}

private fun String.namesVerdict(): Boolean = VERDICT.containsMatchIn(this)

private val VERDICT = Regex("""\bVerdict\b""")

internal fun portIsInterface(scope: KoScope): List<String> = scope
    .classes(includeNested = false, includeLocal = false)
    .withoutException("port-is-interface")
    .filter { it.resideInPackage("..port..") }
    .map { it.where() }

internal fun adapterIsInternal(scope: KoScope): List<String> = scope
    .classes(includeNested = false, includeLocal = false)
    .withoutException("adapter-is-internal")
    .filter { it.resideInPackage("..infrastructure..") }
    .filterNot { it.name.endsWith("Factory") }
    .filterNot { it.hasInternalModifier }
    .map { it.where() }

internal fun noTopLevelFunctions(scope: KoScope): List<String> = scope.files
    .withoutException("no-top-level-functions")
    .filter { it.numFunctions(includeNested = false, includeLocal = false) > 0 }
    .map { it.where() }

internal fun noStatefulObjects(scope: KoScope): List<String> = scope
    .objects(includeNested = true)
    .withoutException("no-stateful-objects")
    .filterNot { it.hasCompanionModifier }
    .filter { obj ->
        obj.hasFunctions(includeNested = false, includeLocal = false) ||
            obj.properties(includeNested = true).any { it.isVar }
    }.map { it.where() }

internal fun noCompanionLogic(scope: KoScope): List<String> = scope
    .objects(includeNested = true)
    .withoutException("no-companion-logic")
    .filter { it.hasCompanionModifier }
    .filter { companion ->
        companion.functions(includeNested = false, includeLocal = false).any { function ->
            val body = function.text
            body.contains("if ") || body.contains("when ") || body.contains("when(")
        }
    }.map { it.where() }

internal fun oneTopLevelClass(scope: KoScope): List<String> = scope.files
    .withoutException("one-top-level-class")
    .filter { file ->
        val types =
            file.numClasses(includeNested = false, includeLocal = false) +
                file.numInterfaces(includeNested = false) +
                file.numObjects(includeNested = false)
        types > 1
    }.map { it.where() }

internal fun nestedImplIsPure(scope: KoScope): List<String> = scope
    .classes(includeNested = true, includeLocal = false)
    .withoutException("nested-impl-is-pure")
    .filter { it.name in NESTED_IMPL_ALLOW }
    .filter { it.containingFile.hasFrameworkImport() }
    .map { it.where() }

internal fun webOneUsecase(scope: KoScope): List<String> {
    // A parameter's type is a name, not a resolved declaration, so the marker is
    // applied once to collect the names and the count is done against that set.
    val useCaseNames = scope.useCaseInterfaces().map { it.name }.toSet()
    return scope
        .classes(includeNested = false, includeLocal = false)
        .withoutException("web-one-usecase")
        .filter { it.resideInPackage("..web..") }
        .filter { klass ->
            val useCaseParams =
                klass.primaryConstructor
                    ?.parameters
                    .orEmpty()
                    .count { parameter -> parameter.type.name in useCaseNames }
            useCaseParams > 1
        }.map { it.where() }
}
