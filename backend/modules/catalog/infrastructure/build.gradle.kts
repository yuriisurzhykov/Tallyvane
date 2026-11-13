plugins {
    id("tallyvane.adapter-module")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.modules.catalog.application)
    // `api`: the factory hands the catalog out as its contract.
    api(projects.modules.catalog.contract)
    implementation(libs.kotlinx.serialization.json)
}
