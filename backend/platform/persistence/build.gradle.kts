plugins {
    id("tallyvane.kotlin-module")
    id("tallyvane.integration-test")
    `java-test-fixtures`
}

dependencies {
    api(projects.platform.kernel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    runtimeOnly(libs.postgresql)

    testFixturesImplementation(libs.testcontainers.postgresql)

    integrationTestImplementation(testFixtures(project()))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestImplementation(libs.exposed.core)
    integrationTestImplementation(libs.exposed.jdbc)
    integrationTestImplementation(libs.hikaricp)
    integrationTestRuntimeOnly(libs.postgresql)
}
