package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

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
