plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.gradle.kotlin.plugin)
    implementation(libs.gradle.detekt.plugin)
    implementation(libs.gradle.ktlint.plugin)
}
