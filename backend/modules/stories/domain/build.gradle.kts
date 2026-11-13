plugins {
    id("tallyvane.pure-module")
}

dependencies {
    // `api`: a story is told in `UntrustedText` and names its facts with the kernel's ids.
    api(projects.platform.kernel)

    testImplementation(testFixtures(projects.platform.kernel))
}
