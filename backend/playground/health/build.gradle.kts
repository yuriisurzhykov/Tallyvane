plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.health.HealthSpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(projects.platform.observability)
    // Since slice 12 this spike serves the real endpoints instead of printing a hand-written
    // approximation of them, so it needs what mounts them.
    implementation(projects.platform.health)
    implementation(projects.platform.http)
    implementation(projects.platform.kernel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
}
