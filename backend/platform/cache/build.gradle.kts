plugins {
    id("tallyvane.pure-module")
}

dependencies {
    api(projects.platform.kernel)

    testImplementation(testFixtures(projects.platform.kernel))
}
