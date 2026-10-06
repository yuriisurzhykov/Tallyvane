plugins {
    id("tallyvane.pure-module")
}

dependencies {
    // `api`: `SignIns` takes the browser's `Secret`, and a redeemed sign-in names the account through
    // `identity`'s contract, so whoever calls it needs both on its own compile classpath.
    api(projects.platform.kernel)
    api(projects.modules.identity.contract)
    // Not used yet; `modules.yaml` requires the edge to every module this one reads.
    implementation(projects.modules.journal.contract)
    // Not used yet; `modules.yaml` requires every contract layer to name `platform:events`.
    implementation(projects.platform.events)
}
