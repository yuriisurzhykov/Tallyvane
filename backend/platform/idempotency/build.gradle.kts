plugins {
    id("tallyvane.pure-module")
    // The fake ledger and the conformance suite are consumed by `platform:http` and
    // `platform:persistence`; `src/test` is not visible across a project boundary and
    // `src/main` would ship them (ADR-044).
    `java-test-fixtures`
}

dependencies {
    api(projects.platform.kernel)
    // `api`: the sweep is started in a scope the caller owns and hands back its `Job`.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testFixturesApi(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(projects.platform.kernel))
}
