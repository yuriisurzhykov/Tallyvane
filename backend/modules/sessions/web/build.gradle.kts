plugins {
    id("tallyvane.web-module")
}

dependencies {
    // `api`: the composition root builds the routes from the use cases they call.
    api(projects.modules.sessions.application)
    // `api`: every route here is a `RouteModule`, and `SessionCallers` is a `Callers`.
    api(projects.platform.http)
    implementation(libs.ktor.server.core)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(testFixtures(projects.modules.sessions.application))
    testImplementation(testFixtures(projects.platform.http))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(testFixtures(projects.platform.idempotency))
}
