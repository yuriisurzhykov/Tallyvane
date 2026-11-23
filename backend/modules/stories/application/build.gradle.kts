plugins {
    id("tallyvane.pure-module")
    // The fakes and the conformance suites of the ports are run by the infrastructure tests too (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: the ports and outcomes speak in the story domain.
    api(projects.modules.stories.domain)
    // `api`: the skills a person may be credited with come from the catalog.
    api(projects.modules.catalog.contract)
    // `api`: `StoriesExperience` implements the contract, and the composition root hands it out as that.
    api(projects.modules.stories.contract)
    api(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesApi(projects.modules.stories.domain)
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.modules.catalog.contract))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
