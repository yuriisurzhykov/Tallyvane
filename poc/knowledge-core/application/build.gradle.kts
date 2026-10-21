plugins {
    `java-test-fixtures`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.domain)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testFixturesApi(projects.domain)
    testFixturesApi(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.core)
}
