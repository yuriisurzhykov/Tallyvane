plugins {
    id("tallyvane.pure-module")
    // The recorder that stands for the journal in the tests of the modules that tell it things; it is the
    // same one for all of them, and `src/test` is not visible across a project boundary (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: the journal is told whose account it is by `identity`'s `AccountId`.
    api(projects.modules.identity.contract)
    // `api`: every contract layer may name the kernel, and `modules.yaml` requires the edge to be drawn.
    api(projects.platform.kernel)
    // `api`: every contract layer carries the edge to the events, as `identity`'s does.
    api(projects.platform.events)

    testFixturesApi(projects.modules.identity.contract)
}
