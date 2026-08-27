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

// `check` compiles these tests but does not run them: running costs a container (ADR-057).
// The message is a literal, because the configuration cache cannot serialise captured
// script values.
tasks.register("integrationTestNotice") {
    group = "verification"
    description = "States that integration tests were compiled but not run by this check."
    doLast {
        logger.lifecycle("check compiled integrationTest but did not run it; use './gradlew integrationTest'.")
    }
}

tasks.named("check") {
    dependsOn("integrationTestNotice", "compileIntegrationTestKotlin")
}

// detekt's default source set list does not know about a suite added here.
extensions.configure<DetektExtension> {
    source.from("src/integrationTest/kotlin")
}
