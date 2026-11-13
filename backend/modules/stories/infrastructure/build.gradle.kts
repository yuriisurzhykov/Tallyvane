plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
    // Reads the model's JSON answers.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.modules.stories.application)
    // Not used yet; `modules.yaml` requires the edge on every infrastructure layer.
    implementation(projects.modules.stories.contract)
    implementation(projects.platform.llm)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.modules.stories.application))
    testImplementation(testFixtures(projects.platform.llm))
    testImplementation(testFixtures(projects.platform.kernel))

    integrationTestImplementation(testFixtures(projects.modules.stories.application))
    integrationTestImplementation(testFixtures(projects.platform.persistence))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    integrationTestImplementation(projects.platform.persistence)
    integrationTestImplementation(projects.platform.kernel)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestRuntimeOnly(libs.postgresql)
}
