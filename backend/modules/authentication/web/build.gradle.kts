plugins {
    id("tallyvane.web-module")
    // Generates the serializers of the request and response bodies.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // `api`: the composition root builds the routes from the use cases they call.
    api(projects.modules.authentication.application)
    // `api`: every route here is a `RouteModule`, and whoever mounts one needs the type.
    api(projects.platform.http)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(testFixtures(projects.modules.authentication.application))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(testFixtures(projects.platform.idempotency))
}
