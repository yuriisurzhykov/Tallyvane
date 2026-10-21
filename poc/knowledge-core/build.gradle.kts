import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Every layer is a Kotlin/JVM 21 module with warnings as errors and kotest on JUnit 5. What a layer
// may depend on is spelled out in its own build file, which is how `domain` stays free of frameworks.
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            allWarningsAsErrors.set(true)
            optIn.add("kotlin.uuid.ExperimentalUuidApi")
        }
    }

    val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(catalog.findLibrary("kotest-runner-junit5").get())
        "testImplementation"(catalog.findLibrary("kotest-assertions-core").get())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
