plugins {
    id("tallyvane.kotlin-module")
    id("application")
}

application {
    mainClass.set("tallyvane.migrate.MigrateKt")
}

dependencies {
    implementation(projects.platform.persistence)
    implementation(projects.modules.identity.infrastructure)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
}
