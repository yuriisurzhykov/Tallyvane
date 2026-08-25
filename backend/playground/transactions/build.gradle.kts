plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.transactions.TransactionsSpikeKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    runtimeOnly(libs.postgresql)
}
