plugins {
    id("tallyvane.kotlin-module")
    id("tallyvane.integration-test")
    `java-test-fixtures`
}

dependencies {
    api(projects.platform.kernel)

    testFixturesImplementation(libs.testcontainers.postgresql)

    integrationTestImplementation(testFixtures(project()))
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestRuntimeOnly(libs.postgresql)
}
