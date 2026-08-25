plugins {
    id("tallyvane.kotlin-module")
    id("application")
}

application {
    mainClass.set("tallyvane.migrate.MigrateKt")
}

dependencies {
    implementation(projects.platform.persistence)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
}
