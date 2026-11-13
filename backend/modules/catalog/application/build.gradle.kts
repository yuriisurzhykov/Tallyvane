plugins {
    id("tallyvane.pure-module")
}

dependencies {
    // `api`: `Catalog` implements the contract, and the composition root hands it out as that.
    api(projects.modules.catalog.contract)
    implementation(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires the edge on every application layer.
    implementation(projects.platform.events)
}
