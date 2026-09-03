import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("jvm-test-suite")
}

/**
 * A test suite for tests that need something outside the JVM — a real Postgres
 * (`platform:persistence`), or a native library this development machine and CI's own runner do
 * not carry (`identity:infrastructure`'s Argon2id adapter).
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

// The JVM Test Suite plugin does not associate a suite's compilation with `main` on its own
// (KT-60767) — only the built-in `test` source set gets that for free. Without this, a suite
// can compile against `main`'s public API but not its `internal` declarations, which is exactly
// what a real adapter's own conformance spec needs to construct the adapter in the first place.
extensions.configure<KotlinJvmProjectExtension> {
    target.compilations.named("integrationTest") {
        associateWith(target.compilations.getByName("main"))
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
