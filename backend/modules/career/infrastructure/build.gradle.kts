plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
}

dependencies {
    implementation(projects.modules.career.application)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    integrationTestImplementation(testFixtures(projects.modules.career.application))
    integrationTestImplementation(testFixtures(projects.platform.persistence))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    integrationTestImplementation(projects.platform.persistence)
    integrationTestImplementation(projects.platform.kernel)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestRuntimeOnly(libs.postgresql)
}
