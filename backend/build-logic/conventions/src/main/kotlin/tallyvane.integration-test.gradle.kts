import dev.detekt.gradle.extensions.DetektExtension

plugins {
    id("jvm-test-suite")
}

/**
 * A test suite for tests that need something outside the JVM — today a real
 * Postgres, from slice 13 a running server.
 *
 * Applied by the modules that have such tests, not by every module, so no empty
 * source set appears where there is nothing to put in it.
 */
testing.suites.register<JvmTestSuite>("integrationTest") {
    useJUnitJupiter()
    dependencies {
        implementation(project())
    }
}

// Deliberately NOT wired into `check`: these tests cost a container, and running
// them is opt-in locally (`./gradlew integrationTest`) and mandatory in CI.
// The message is a literal rather than built from captured script values, which
// the configuration cache cannot serialise.
tasks.register("integrationTestNotice") {
    group = "verification"
    description = "States that integration tests were not part of this check."
    doLast {
        logger.lifecycle("check excluded integrationTest; run './gradlew integrationTest' to include it.")
    }
}

tasks.named("check") {
    dependsOn("integrationTestNotice")
}

// detekt's default source set list does not know about a suite added here.
extensions.configure<DetektExtension> {
    source.from("src/integrationTest/kotlin")
}
