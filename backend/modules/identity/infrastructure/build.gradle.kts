plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
    // Generates the serializer for GoogleOAuthGatewayOverHttp's own @Serializable response shape.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.modules.identity.application)
    api(projects.modules.identity.contract)
    implementation(projects.platform.cache)
    // `-nolibs`: no native binary bundled — installed on the image instead
    // (backend/Dockerfile), the maintainer's own recommended shape.
    implementation(libs.argon2.jvm.nolibs)
    // Outbound calls to Google's token and JWKS endpoints — same engine as the server side.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Verifies a Google ID token's signature against its published JWKS — see this module's own
    // README for why this is not hand-rolled.
    implementation(libs.nimbus.jose.jwt)
    // Encrypts a TOTP secret at rest, so it can be read back to compute a code — see this
    // module's own README for why this is not hand-rolled either.
    implementation(libs.tink)

    testImplementation(testFixtures(projects.modules.identity.application))
    testImplementation(testFixtures(projects.platform.kernel))
    // MockEngine: drives GoogleOAuthGatewayOverHttp's own HTTP calls against a canned response,
    // without a real socket.
    testImplementation(libs.ktor.client.mock)

    integrationTestImplementation(testFixtures(projects.modules.identity.application))
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
}
