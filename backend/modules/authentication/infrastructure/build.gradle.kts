plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
    // Generates the serializer for the one JSON shape read back from Google's token endpoint.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // The domain comes with the application as `api`, which is the only way `modules.yaml` lets
    // infrastructure see it.
    implementation(projects.modules.authentication.application)
    // Not used yet; `modules.yaml` requires the edge on every infrastructure layer.
    implementation(projects.modules.authentication.contract)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    // The one outbound call there is: the code Google sent back, traded at its token endpoint.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    // Checks the signature of the ID token Google signs, against Google's published keys.
    implementation(libs.nimbus.jose.jwt)
    // Seals a TOTP seed in the database: it must be read back to compute a code, so it cannot be hashed.
    implementation(libs.tink)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(projects.modules.authentication.application))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.core)

    integrationTestImplementation(testFixtures(projects.modules.authentication.application))
    integrationTestImplementation(testFixtures(projects.platform.persistence))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    integrationTestImplementation(projects.platform.persistence)
    integrationTestImplementation(projects.platform.kernel)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestImplementation(libs.tink)
    integrationTestImplementation(libs.exposed.core)
    integrationTestImplementation(libs.exposed.jdbc)
    integrationTestRuntimeOnly(libs.postgresql)
}
