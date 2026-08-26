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
    // `classes()` never returns an object. Nested implementations were a third
    // hole: `includeNested = false` never saw `class Registry { class SignIn :
    // SignInUseCase }`, so the required nesting was asserted only by not looking.
    val markers = scope.useCaseInterfaces().map { it.name }.toSet() + USE_CASE_MARKER
    val classes =
        scope
            .classes(includeNested = true, includeLocal = false)
            .withoutException("usecase-is-interface")
            .filter { klass -> klass.parentInterfaces().any { it.name in markers } }
            .filterNot { it.isNestedInOwnUseCaseInterface(markers) }
            .map { it.where() }
    val objects =
        scope
            .objects(includeNested = true)
            .withoutException("usecase-is-interface")
            .filter { declaration -> declaration.parentInterfaces().any { it.name in markers } }
            .filterNot { it.isNestedInOwnUseCaseInterface(markers) }
            .map { it.where() }
    return classes + objects
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

/**
 * Every [FAILURE_MARKER] carrier is either a sealed root or nested inside one.
 *
 * The point is to make one mapping table possible. A module that scatters failures as loose
 * members of its outcome forces `Problems` per case, and then a forgotten one is a 500 nobody
 * predicted. Grouped under one sealed branch, the table's `when` is exhaustive and the compiler
 * refuses to let a new case go unmapped.
 */
internal fun failureGroupsUnderRoot(scope: KoScope): List<String> {
    // Two collections, both structural. Sealedness is asked of interfaces, which expose the
    // modifier that the union type of `classesAndInterfacesAndObjects` does not; and "grouped"
    // means "not declared at the top level of its file", which the file's own non-nested
    // declarations answer exactly. A first draft asked the file's text whether the declaration
    // was indented, and it flagged nothing — the fixture stayed green, which is how the
    // heuristic was found to be one.
    val sealedRoots = scope
        .interfaces(includeNested = true)
        .filter { declaration -> declaration.hasSealedModifier }
        .map { declaration -> declaration.name }
        .toSet()

    val topLevel = scope.files
        .flatMap { file ->
            file.classes(includeNested = false, includeLocal = false).map { it.name } +
                file.interfaces(includeNested = false).map { it.name } +
                file.objects(includeNested = false).map { it.name }
        }.toSet()

    return scope
        .classesAndInterfacesAndObjects(includeNested = true)
        .withoutException("failure-groups-under-root")
        .filter { declaration -> declaration.parentInterfaces().any { it.name == FAILURE_MARKER } }
        .filterNot { declaration -> declaration.name in sealedRoots }
        .filter { declaration -> declaration.name in topLevel }
        .map { it.where() }
}

/**
 * Every sealed failure root has a [PROBLEMS_PORT] implementation naming it.
 *
 * Catches the half-built case: the branch exists, a route can return it, and nothing turns it
 * into an answer. Matched by name in the parent type's text because a type argument is what
 * carries the link — `Problems<SaveJobOutcome.Failed>` — and that is the only place it appears.
 */
internal fun failureHasProblems(scope: KoScope): List<String> {
    val mapped = scope.files.joinToString("\n") { file -> file.codeText() }
    return scope
        .interfaces(includeNested = true)
        .withoutException("failure-has-problems")
        .filter { declaration -> declaration.parentInterfaces().any { it.name == FAILURE_MARKER } }
        .filter { declaration -> declaration.hasSealedModifier }
        .filterNot { declaration ->
            Regex("""$PROBLEMS_PORT<[^>]*\b${Regex.escape(declaration.name)}\b""").containsMatchIn(mapped)
        }.map { it.where() }
}

/**
 * `Problem` has no public way to make one: no public constructor, no companion factories.
 *
 * This guards the foundation everything else in ADR-062 stands on. The first design *did* have
 * public factories, and with them a route could answer with a problem of its own and never touch
 * its module's mapping table — the tables were required to exist and required to be used by
 * nothing. The fix was to move every factory onto `Answers`, which only the renderer holds.
 *
 * One convenience factory added here in six months would undo all of it silently: nothing would
 * fail, and the contract would quietly become a convention again.
 */
internal fun problemHasNoPublicSource(scope: KoScope): List<String> = scope
    .classes(includeNested = true, includeLocal = false)
    .withoutException("problem-has-no-public-source")
    .filter { declaration -> declaration.name == PROBLEM_TYPE }
    .filter { declaration ->
        declaration.primaryConstructor?.hasInternalModifier != true ||
            declaration.objects(includeNested = false).any { nested -> nested.hasCompanionModifier }
    }.map { it.where() }

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
