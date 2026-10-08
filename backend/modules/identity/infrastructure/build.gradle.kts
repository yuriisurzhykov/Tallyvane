plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
}

dependencies {
    implementation(projects.modules.identity.application)
    implementation(projects.modules.identity.contract)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)

    integrationTestImplementation(testFixtures(projects.modules.identity.application))
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
