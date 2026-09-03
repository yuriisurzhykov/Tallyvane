plugins {
    id("tallyvane.pure-module")
}

dependencies {
    api(projects.platform.kernel)
    // Required by modules.yaml's generic contract: layer allow-list, not by anything this layer
    // publishes yet — no module reads an identity event this pass (modules.yaml: publishes: []).
    api(projects.platform.events)
}
