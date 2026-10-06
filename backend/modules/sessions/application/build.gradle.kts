plugins {
    id("tallyvane.pure-module")
    // The conformance suite of `Sessions` is run by the infrastructure module's tests as well as this one's,
    // and `src/test` is not visible across a project boundary (ADR-046). The fake is shared the same way, so
    // the web layer can drive the real use cases.
    `java-test-fixtures`
}

dependencies {
    // `api`: the port speaks in `Session`, so whoever implements it needs the domain.
    api(projects.modules.sessions.domain)
    // `api`: `OpenSession` is built from the contract it redeems a sign-in through.
    api(projects.modules.authentication.contract)
    // `api`: a signed-in caller is named by `identity`'s `AccountId`.
    api(projects.modules.identity.contract)
    // The use cases tell the journal what they did, in their own transaction (ADR-095).
    api(projects.modules.journal.contract)
    implementation(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)

    testFixturesApi(projects.platform.kernel)
    testFixturesApi(testFixtures(projects.modules.journal.contract))
    testFixturesImplementation(testFixtures(projects.platform.kernel))
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
