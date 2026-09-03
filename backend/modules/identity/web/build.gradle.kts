plugins {
    id("tallyvane.web-module")
    // Generates the serializers for this module's own @Serializable request/response DTOs — the
    // runtime library alone does not, per `platform:http`'s own build file comment.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.modules.identity.application)
    api(projects.modules.identity.contract)
    api(projects.platform.http)
    implementation(libs.ktor.server.core)

    testImplementation(testFixtures(projects.modules.identity.application))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(projects.platform.observability)
}
