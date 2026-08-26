plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.health.HealthSpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(projects.platform.observability)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
}
