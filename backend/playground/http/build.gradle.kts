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
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)
    // The binding, so the JSON log lines actually appear. platform:observability ships the
    // configuration fragment and only the facade; choosing logback is the runner's call.
    runtimeOnly(libs.logback.classic)
}
