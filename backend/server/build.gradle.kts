plugins {
    id("tallyvane.kotlin-module")
    id("tallyvane.integration-test")
    id("application")
}

application {
    // The class, not `ApplicationKt`: `no-top-level-functions` covers `server/`, and ADR-010
    // records why a recorded exception was not spent on the entry point.
    mainClass.set("tallyvane.server.Application")
}

dependencies {
    // `health` brings `http`, `observability` and `kernel` with it as `api`, but naming them
    // here anyway: a composition root that depends on a platform module only by accident of
    // someone else's `api` edge is a root whose dependencies are not reviewable.
    implementation(projects.platform.kernel)
    implementation(projects.platform.idempotency)
    implementation(projects.platform.persistence)
    implementation(projects.platform.observability)
    implementation(projects.platform.http)
    implementation(projects.platform.health)
    // Each capability the process serves, by the layers the root wires: the use cases, the adapters that
    // keep their state, and the routes.
    implementation(projects.modules.identity.contract)
    implementation(projects.modules.identity.application)
    implementation(projects.modules.identity.infrastructure)
    implementation(projects.modules.identity.web)
    implementation(projects.modules.authentication.contract)
    implementation(projects.modules.authentication.application)
    implementation(projects.modules.authentication.infrastructure)
    implementation(projects.modules.authentication.web)
    implementation(projects.modules.sessions.domain)
    implementation(projects.modules.sessions.application)
    implementation(projects.modules.sessions.infrastructure)
    implementation(projects.modules.sessions.web)
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
    // A case mounts the real edge over the real database with a route of its own, so it names the
    // edge, the ledger it asks and the engine it runs on, as `Wiring` does.
    integrationTestImplementation(projects.platform.http)
    // A case that signs somebody in plants what a completed sign-in would have left, through the same
    // adapters the process reads with.
    integrationTestImplementation(projects.modules.identity.contract)
    integrationTestImplementation(projects.modules.identity.application)
    integrationTestImplementation(projects.modules.identity.infrastructure)
    integrationTestImplementation(projects.modules.sessions.domain)
    integrationTestImplementation(projects.modules.sessions.application)
    integrationTestImplementation(projects.modules.sessions.infrastructure)
    integrationTestImplementation(projects.platform.idempotency)
    integrationTestImplementation(libs.ktor.server.cio)
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
