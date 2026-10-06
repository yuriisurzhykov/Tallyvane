plugins {
    id("tallyvane.pure-module")
    // The conformance suite of `Entries` is run by the infrastructure module's tests as well as this one's, and
    // `src/test` is not visible across a project boundary (ADR-046). The fake is shared the same way.
    `java-test-fixtures`
}

dependencies {
    // `api`: the ports speak in `Entry`, so whoever implements them needs the domain.
    api(projects.modules.journal.domain)
    // `api`: `Journal` implements the contract, and the composition root hands it out as that.
    api(projects.modules.journal.contract)
    // `api`: a person is named by `identity`'s `AccountId`.
    api(projects.modules.identity.contract)
    implementation(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesApi(projects.modules.journal.domain)
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.modules.journal.contract))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
