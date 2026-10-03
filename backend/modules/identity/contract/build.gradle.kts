plugins {
    id("tallyvane.pure-module")
}

dependencies {
    // `api`: `Registrant` carries a `kotlin.time.Instant` and nothing from the kernel, but every contract
    // layer may name the kernel and `modules.yaml` requires the edge to be drawn.
    api(projects.platform.kernel)
    // `api`: `AccountDeleted` is a `DomainEvent`, and whoever answers it names that type too.
    api(projects.platform.events)
}
