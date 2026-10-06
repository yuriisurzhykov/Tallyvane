plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
}

dependencies {
    // The domain comes with the application as `api`, which is the only way `modules.yaml` lets
    // infrastructure see it.
    implementation(projects.modules.journal.application)
    // Not used yet; `modules.yaml` requires the edge on every infrastructure layer.
    implementation(projects.modules.journal.contract)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.slf4j.api)

    testImplementation(testFixtures(projects.modules.journal.application))

    integrationTestImplementation(testFixtures(projects.modules.journal.application))
    integrationTestImplementation(testFixtures(projects.platform.persistence))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    integrationTestImplementation(projects.platform.persistence)
    integrationTestImplementation(projects.platform.kernel)
    integrationTestImplementation(libs.exposed.core)
    integrationTestImplementation(libs.exposed.jdbc)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestRuntimeOnly(libs.postgresql)
}
