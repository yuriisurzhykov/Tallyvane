plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.isolation.IsolationSpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    runtimeOnly(libs.postgresql)
}
