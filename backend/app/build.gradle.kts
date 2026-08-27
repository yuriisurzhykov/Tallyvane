plugins {
    id("tallyvane.kotlin-module")
    id("tallyvane.integration-test")
    id("application")
}

application {
    // The class, not `ApplicationKt`: `no-top-level-functions` covers `app/`, and ADR-010
    // records why a recorded exception was not spent on the entry point.
    mainClass.set("tallyvane.app.Application")
}

dependencies {
    // `health` brings `http`, `observability` and `kernel` with it as `api`, but naming them
    // here anyway: a composition root that depends on a platform module only by accident of
    // someone else's `api` edge is a root whose dependencies are not reviewable.
    implementation(projects.platform.kernel)
    implementation(projects.platform.persistence)
    implementation(projects.platform.observability)
    implementation(projects.platform.http)
    implementation(projects.platform.health)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    // The composition root is the one place allowed to know which logging backend is installed:
    // slf4j has no API for setting a level, and ADR-056 puts the level under configuration.
    implementation(libs.logback.classic)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(testFixtures(projects.platform.kernel))

    integrationTestImplementation(testFixtures(projects.platform.persistence))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    // The suite talks HTTP, so it names statuses in Ktor's vocabulary rather than as bare numbers.
    integrationTestImplementation(libs.ktor.server.core)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestImplementation(libs.testcontainers.postgresql)
    // A case that closes the pool has to issue a real statement to notice: an empty transaction
    // never asks the pool for a connection, so Exposed is named here rather than borrowed.
    integrationTestImplementation(libs.exposed.core)
    integrationTestImplementation(libs.exposed.jdbc)
    integrationTestRuntimeOnly(libs.postgresql)
}
