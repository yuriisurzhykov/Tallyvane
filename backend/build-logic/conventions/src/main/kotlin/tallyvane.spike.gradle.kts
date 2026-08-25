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
tasks.named<JavaExec>("run") {
    systemProperties(
        providers
            .provider {
                project.properties
                    .filterKeys { key -> key.startsWith("spike.") }
                    .mapValues { (_, value) -> value.toString() }
            }.get(),
    )
}
