plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
}

dependencies {
    // The domain comes with the application as `api`, which is the only way `modules.yaml` lets
    // infrastructure see it.
    implementation(projects.modules.sessions.application)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)

    integrationTestImplementation(testFixtures(projects.modules.sessions.application))
    integrationTestImplementation(testFixtures(projects.platform.persistence))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    integrationTestImplementation(projects.platform.persistence)
    integrationTestImplementation(projects.platform.kernel)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestRuntimeOnly(libs.postgresql)
}
