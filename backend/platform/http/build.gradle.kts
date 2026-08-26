plugins {
    id("tallyvane.pure-module")
    // Generates the serializers for @Serializable. The kotlinx-serialization library alone
    // does not: without this plugin every @Serializable class fails to compile.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.platform.kernel)
    // `api`: `RouteModule.install` takes Ktor's own `Route`, so anyone implementing one needs
    // Ktor on their compile classpath. ADR-050 accepts that this contract names Ktor.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization.json)
    // `implementation`: the engine and the plugins are this module's own choices. A consumer
    // registering routes needs none of them, and `app` names the engine itself.
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(projects.platform.observability)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.cio)
    testImplementation(projects.platform.observability)
    testImplementation(testFixtures(projects.platform.kernel))
}
