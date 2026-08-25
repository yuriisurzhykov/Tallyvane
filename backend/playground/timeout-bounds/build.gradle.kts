plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.timeoutbounds.TimeoutBoundsSpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.postgresql)
}
