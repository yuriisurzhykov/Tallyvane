tasks.register("check") {
    group = "verification"
    description = "Detekt and tests for every leaf of this included build"
    dependsOn(
        ":conventions:check",
        ":graph:check",
        ":verification:check",
        ":root:check",
        ":ktlint-rules:check",
    )
}
