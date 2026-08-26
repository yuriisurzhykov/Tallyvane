plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.ddllocks.DdlLocksSpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
}
