plugins {
    id("tallyvane.pure-module")
}

dependencies {
    api(projects.platform.kernel)
    implementation(libs.kotlinx.coroutines.core)
}
