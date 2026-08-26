import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("application")
}

/**
 * A spike under `playground/`: code written to answer one question, kept so the answer
 * stays checkable.
 *
 * Deliberately thinner than `tallyvane.kotlin-module`: no ktlint, no detekt, no
 * warnings-as-errors. A spike that has to satisfy production lint is a spike nobody
 * writes — the first one here was failed by detekt for the literal `3`, which taught
 * nothing about the question it was answering.
 *
 * It does still have to compile, and `check` sees to that: a spike that no longer
 * compiles is a lie about what was learned, not a record of it.
 */
extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

extensions.configure<KotlinJvmProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.named("check") {
    dependsOn("classes")
}

// `./gradlew :playground:<name>:run -Pspike.url=...` reaches the spike as a system
// property; Gradle does not forward these on its own.
//
// 2026-08-26: the first version read `project.properties` inside a provider and called
// `.get()` on it during configuration, and under the configuration cache — on by default
// here — that silently stopped working. The eager read is not registered as a
// configuration input, so a cached entry kept the map from whichever run created it:
// measured, `-Pspike.port=9007` left the spike on its default 8099, while the same
// command with `--no-configuration-cache` bound 9007. Every `-Pspike.*` in every
// playground README was affected, including the database URLs.
//
// `gradlePropertiesPrefixedBy` is a tracked input, and passing the values through an
// argument provider defers reading them to execution time, so changing one invalidates
// the entry instead of being ignored by it.
tasks.named<JavaExec>("run") {
    val forwarded = providers.gradlePropertiesPrefixedBy("spike.")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            forwarded.get().map { (key, value) -> "-D$key=$value" }
        },
    )
}
