package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

internal fun portHasContractSuite(scope: KoScope): List<String> {
    val contractNames =
        scope
            .classesAndInterfacesAndObjects(includeNested = true)
            .map { it.name }
            .filter { it.endsWith("Contract") }
            .toSet()
    return scope
        .interfaces(includeNested = false)
        .withoutException("port-has-contract-suite")
        .filter { it.resideInPackage("..port..") }
        .filter { port ->
            val implementations =
                scope
                    .classes(includeNested = true, includeLocal = false)
                    .count { klass -> klass.parentInterfaces().any { parent -> parent.name == port.name } }
            implementations > 1 && "${port.name}Contract" !in contractNames
        }.map { it.where() }
}

internal fun usecaseHasTest(scope: KoScope): List<String> {
    val testNames = scope.classes(includeNested = false, includeLocal = false).map { it.name }.toSet()
    return scope
        .applicationUseCases()
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
            file.text.contains("when") && kinds.any { kind -> file.text.contains(kind) }
        }.map { it.where() }
}
