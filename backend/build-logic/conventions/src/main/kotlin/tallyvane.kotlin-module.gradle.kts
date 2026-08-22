import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

extensions.configure<KotlinJvmProjectExtension> {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

configure<KtlintExtension> {
    version.set(libs.findVersion("ktlintEngine").get().requiredVersion)
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
}

dependencies {
    testImplementation(libs.findLibrary("kotest-runner-junit5").get())
    testImplementation(libs.findLibrary("kotest-assertions-core").get())
    add("ktlintRuleset", "tallyvane.gradle:ktlint-rules:0.0.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("ktlintCheck", "detekt")
}

tasks.register("arch") {
    group = "verification"
    description = "Style and analysis for this module; the repo aggregator also runs the graph and Konsist."
    dependsOn("ktlintCheck", "detekt")
}
