plugins {
    id("tallyvane.web-module")
}

dependencies {
    api(projects.modules.identity.application)
    api(projects.modules.identity.contract)
    api(projects.platform.http)
}
