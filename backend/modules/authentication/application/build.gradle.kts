plugins {
    id("tallyvane.pure-module")
    // The conformance suites are run by the infrastructure module's tests as well as this one's, and
    // `src/test` is not visible across a project boundary (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: the ports speak in `Attempt` and `PolicyVersion`, so whoever implements or calls them
    // needs the domain on its own compile classpath.
    api(projects.modules.authentication.domain)
    // `api`: `Redemptions` is what the composition root hands out as the contract `sessions` calls.
    api(projects.modules.authentication.contract)
    // `Accounts` is how a Google subject becomes an account (slice 3).
    api(projects.modules.identity.contract)

    // Not used yet. `modules.yaml` requires every application layer to name `platform:events` (a
    // declared edge that goes unused is an error, ARCHITECTURE.md 15.2), and the security journal of
    // ADR-083 is the first thing here that publishes through it.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    // The fakes of the ports are fixtures, not test code: the web layer's tests drive the use cases
    // with them, and `src/test` is not visible across a project boundary (ADR-046).
    testFixturesApi(projects.modules.identity.contract)
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
