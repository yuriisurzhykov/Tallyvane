plugins {
    id("tallyvane.kotlin-module")
    // `ModelFake`, `MemoFake` and `SpendingFake` stand for the model in the tests of every module that asks it
    // (ADR-044, ADR-046).
    `java-test-fixtures`
}

dependencies {
    api(projects.platform.kernel)
    // The memo and the spending are kept in Postgres, in transactions of their own.
    implementation(projects.platform.persistence)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.kotlinx.coroutines.core)
    // The official SDK; nothing outside `AnthropicModel` names it.
    implementation(libs.anthropic.java)

    testFixturesApi(projects.platform.kernel)

    testImplementation(testFixtures(project()))
    testImplementation(testFixtures(projects.platform.kernel))
    testImplementation(libs.kotlinx.coroutines.core)
}
