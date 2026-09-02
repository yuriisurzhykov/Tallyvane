plugins {
    id("tallyvane.pure-module")
    // TokenFactoryConformance (ADR-046) is consumed by this module's own test source set, which
    // is not visible across a project boundary but is what the fake and the real Csprng both need
    // to be checked against. Applied here rather than in the convention plugin so a module that
    // shares nothing does not grow an empty source set.
    `java-test-fixtures`
}

dependencies {
    api(projects.modules.identity.domain)
    api(projects.modules.identity.contract)
    api(projects.platform.kernel)
    // Required by modules.yaml's generic application: layer allow-list, not by any port here
    // yet — nothing in this pass publishes an event (modules.yaml: publishes: []).
    api(projects.platform.events)
    // The facade only, never a backend: ENGINEERING-PRINCIPLES.md "A recovered failure is
    // logged where its meaning is known" — this is a library coordinate, not a `platform:*`
    // module, so `modules.yaml`'s layer allow-list does not govern it (`validateModuleGraph`
    // compares only project-to-project edges).
    implementation(libs.slf4j.api)

    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(testFixtures(projects.platform.kernel))
    // Only to capture what RateLimitedSpec asserts on; the real binding stays server's choice.
    testImplementation(libs.logback.classic)
}
