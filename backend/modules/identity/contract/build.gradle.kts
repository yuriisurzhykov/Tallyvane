plugins {
    id("tallyvane.pure-module")
}

dependencies {
    // `api`: `Registrant` carries a `kotlin.time.Instant` and nothing from the kernel, but every contract
    // layer may name the kernel and `modules.yaml` requires the edge to be drawn.
    api(projects.platform.kernel)
    // Not used yet; `modules.yaml` requires every contract layer to name `platform:events`. The account
    // events of §4.5 are the first thing here that will.
    implementation(projects.platform.events)
}
