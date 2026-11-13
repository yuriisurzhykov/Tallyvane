plugins {
    id("tallyvane.web-module")
    // Generates the serializers of the request and response bodies.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // `api`: the composition root builds the routes from the use cases they call.
    api(projects.modules.models.application)
    // `api`: every route here is a `RouteModule`, and whoever mounts one needs the type.
    api(projects.platform.http)
    implementation(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every web layer.
    implementation(projects.platform.idempotency)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.serialization.json)
}
