plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.pooloccupancy.PoolOccupancySpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(projects.platform.kernel)
    implementation(libs.hikaricp)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.postgresql)
}
