plugins {
    id("tallyvane.spike")
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass.set("tallyvane.playground.http.HttpSpikeKt")
}

dependencies {
    implementation(projects.platform.http)
    implementation(projects.platform.kernel)
    implementation(projects.platform.observability)
    // The spike keeps no state, so it takes the in-memory ledger the tests use.
    implementation(testFixtures(projects.platform.idempotency))
    implementation(testFixtures(projects.platform.kernel))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.kotlinx.serialization.json)
    // The binding, so the JSON log lines actually appear. platform:observability ships the
    // configuration fragment and only the facade; choosing logback is the runner's call.
    runtimeOnly(libs.logback.classic)
}
