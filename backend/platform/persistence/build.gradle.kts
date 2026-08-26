plugins {
    id("tallyvane.kotlin-module")
    id("tallyvane.integration-test")
    `java-test-fixtures`
}

dependencies {
    api(projects.platform.kernel)
    // `implementation`, deliberately, even though `DatabaseAnswers` and
    // `MigrationsApplied` are public `HealthCheck`s: with `api` this module put
    // observability on the compile classpath of everything downstream, and `:migrate`
    // — a command that applies migrations and exits — was carrying it for nothing.
    // Whoever wires a health check already depends on observability to aggregate it.
    implementation(projects.platform.observability)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.exposed.migration.jdbc)
    testFixturesRuntimeOnly(libs.postgresql)
    testFixturesRuntimeOnly(libs.flyway.database.postgresql)

    integrationTestImplementation(testFixtures(project()))
    integrationTestImplementation(testFixtures(projects.platform.kernel))
    // The suite gets this module's `api` and not its `implementation`, so the specs that
    // assert on `Health` name observability themselves. That is the visible cost of the
    // line above, and it is preferred to `:migrate` carrying observability instead.
    integrationTestImplementation(projects.platform.observability)
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
    integrationTestImplementation(libs.kotlinx.coroutines.core)
    integrationTestImplementation(libs.exposed.core)
    integrationTestImplementation(libs.exposed.jdbc)
    integrationTestImplementation(libs.hikaricp)
    integrationTestRuntimeOnly(libs.postgresql)
}
