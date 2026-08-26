plugins {
    id("tallyvane.pure-module")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.platform.kernel)
    // `api`: `HealthRoutes` is a `RouteModule` and takes a `HealthReporter`, so a composition
    // root wiring it has to see both types.
    api(projects.platform.http)
    api(projects.platform.observability)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.cio)
    testImplementation(testFixtures(projects.platform.kernel))
}
