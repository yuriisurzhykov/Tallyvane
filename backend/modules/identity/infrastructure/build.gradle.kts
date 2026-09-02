plugins {
    id("tallyvane.adapter-module")
    id("tallyvane.integration-test")
}

dependencies {
    api(projects.modules.identity.application)
    api(projects.modules.identity.contract)
    implementation(projects.platform.cache)
    // `-nolibs`: no native binary bundled — installed on the image instead
    // (backend/Dockerfile), the maintainer's own recommended shape.
    implementation(libs.argon2.jvm.nolibs)

    testImplementation(testFixtures(projects.modules.identity.application))
    testImplementation(testFixtures(projects.platform.kernel))

    integrationTestImplementation(testFixtures(projects.modules.identity.application))
    integrationTestImplementation(libs.kotest.runner.junit5)
    integrationTestImplementation(libs.kotest.assertions.core)
}
