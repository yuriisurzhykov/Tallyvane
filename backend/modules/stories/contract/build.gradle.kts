plugins {
    id("tallyvane.pure-module")
    // `ExperienceFake` stands for the stories in the tests of the modules that read them (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: every contract layer may name the kernel and the events, and `modules.yaml` requires the edges.
    api(projects.platform.kernel)
    api(projects.platform.events)
    // Not used yet; `modules.yaml` requires the edge to every module this one reads.
    api(projects.modules.catalog.contract)
}
