plugins {
    id("tallyvane.pure-module")
    // The fake and the conformance suite of `Goals` are run by the infrastructure tests too (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: the home names role tracks and stations of the catalog.
    api(projects.modules.catalog.contract)
    // `api`: what a person told is the only evidence the route and the résumé are built from.
    api(projects.modules.stories.contract)
    api(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.modules.catalog.contract))
    testImplementation(testFixtures(projects.modules.stories.contract))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
