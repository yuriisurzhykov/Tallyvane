plugins {
    id("tallyvane.pure-module")
    // `SkillCatalogFake` stands for the catalog in the tests of the modules that read it (ADR-046).
    `java-test-fixtures`
}

dependencies {
    // `api`: every contract layer may name the kernel and the events, and `modules.yaml` requires the edges.
    api(projects.platform.kernel)
    api(projects.platform.events)
}
