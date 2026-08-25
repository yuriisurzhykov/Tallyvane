plugins {
    id("tallyvane.kotlin-module")
}

dependencies {
    api(projects.platform.kernel)
    implementation(libs.kotlinx.coroutines.core)
}
