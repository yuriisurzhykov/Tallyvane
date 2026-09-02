plugins {
    id("tallyvane.adapter-module")
}

dependencies {
    api(projects.modules.identity.application)
    api(projects.modules.identity.contract)
}
