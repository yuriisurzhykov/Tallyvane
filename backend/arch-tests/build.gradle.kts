plugins {
    id("tallyvane.kotlin-module")
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(projects.platform.kernel)
}

tasks.withType<Test>().configureEach {
    val backendRoot = rootProject.projectDir
    val repoRoot = backendRoot.parentFile

    systemProperty("konsist.root", backendRoot.absolutePath)
    systemProperty("repo.root", repoRoot.absolutePath)

    // These specs read the repository through a path handed over as a system
    // property — a String, so Gradle sees no files behind it and serves a cached
    // pass while a violation sits in another module. Measured: a planted
    // `System.currentTimeMillis()` in platform:observability left `check` green
    // with `:arch-tests:test FROM-CACHE`, and failed at once under `--rerun`.
    // Declared as whole trees on purpose: a newly scanned location is covered
    // without anyone remembering to widen this list. See arch-tests/README.md.
    inputs
        .files(
            fileTree(backendRoot) { exclude("**/build/**", "**/.gradle/**", "**/.idea/**") },
            fileTree(File(repoRoot, "docs/adr")),
            // `openapi-covers-routes` reads this, and a path removed from it has to fail the build
            // on the commit that removes it rather than on the next unrelated change.
            File(repoRoot, "docs/openapi.yaml"),
        ).withPropertyName("scannedTrees")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
