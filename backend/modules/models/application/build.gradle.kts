plugins {
    id("tallyvane.pure-module")
    // The fake and the conformance suite of `ProviderBook` are run by the infrastructure tests too (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: only an administrator may see or change the providers, asked through `identity`'s `Admins`.
    api(projects.modules.identity.contract)
    api(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
