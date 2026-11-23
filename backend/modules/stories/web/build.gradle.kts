plugins {
    id("tallyvane.web-module")
    // Generates the serializers of the request and response bodies.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // `api`: the composition root builds the routes from the use cases they call.
    api(projects.modules.stories.application)
    // Not used yet; `modules.yaml` requires the edge on every web layer.
    implementation(projects.modules.stories.contract)
    // `api`: every route here is a `RouteModule`, and whoever mounts one needs the type.
    api(projects.platform.http)
    // Not used yet; `modules.yaml` requires the edges on every web layer.
    implementation(projects.platform.kernel)
    implementation(projects.platform.idempotency)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.serialization.json)
}
