plugins {
    id("tallyvane.pure-module")
}

dependencies {
    api(projects.platform.kernel)
    // `api`: a dependant takes this module in order to log, and needs the facade
    // on its own compile classpath. The binding is `app`'s choice, not a library's.
    api(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.logback.classic)
    testImplementation(libs.kotlinx.serialization.json)
}
