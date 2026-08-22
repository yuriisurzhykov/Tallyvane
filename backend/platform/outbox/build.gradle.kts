plugins {
    id("tallyvane.kotlin-module")
}

dependencies {
    api(projects.platform.kernel)
    api(projects.platform.persistence)
}
