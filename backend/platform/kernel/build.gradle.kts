plugins {
    id("tallyvane.pure-module")
    // The fakes and the conformance suite are consumed by other modules' tests;
    // `src/test` is not visible across a project boundary and `src/main` would
    // ship them (ADR-044). Applied here rather than in the convention plugin so
    // modules that share nothing do not grow an empty source set.
    `java-test-fixtures`
}

dependencies {
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.kotest.assertions.core)
}
