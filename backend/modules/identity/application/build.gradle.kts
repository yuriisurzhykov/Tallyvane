plugins {
    id("tallyvane.pure-module")
    // The conformance suite of `KeptAccounts` is run by the infrastructure module's tests as well as
    // this one's, and `src/test` is not visible across a project boundary (ADR-046). The fake is
    // shared the same way, so `authentication` can test against the real `AccountDirectory`.
    `java-test-fixtures`
}

dependencies {
    // `api`: the port speaks in `Account`, so whoever implements it needs the domain.
    api(projects.modules.identity.domain)
    // `api`: `AccountDirectory` is what the composition root hands out as the contract.
    api(projects.modules.identity.contract)
    implementation(projects.platform.kernel)
    // Not used yet, as in `authentication`: `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesApi(projects.modules.identity.contract)
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
