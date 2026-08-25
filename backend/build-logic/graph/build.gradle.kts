plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.layout.projectDirectory.dir("..").file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

gradlePlugin {
    plugins {
        register("graph") {
            id = "tallyvane.graph"
            implementationClass = "tallyvane.gradle.graph.ModuleGraphPlugin"
        }
    }
}

dependencies {
    implementation(libs.snakeyaml)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("detekt")
}
