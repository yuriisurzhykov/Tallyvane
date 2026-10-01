plugins {
    id("tallyvane.kotlin-module")
    id("application")
}

application {
    mainClass.set("tallyvane.migrate.MigrateKt")
}

dependencies {
    implementation(projects.platform.persistence)
    // A module's migrations are found on the classpath (ADR-059), so the module that owns them has to
    // be on this one. Nothing is called from it: this command only applies what it carries.
    runtimeOnly(projects.modules.authentication.infrastructure)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
}
