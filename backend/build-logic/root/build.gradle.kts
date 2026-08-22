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
    source.setFrom("src/main/kotlin")
}

gradlePlugin {
    plugins {
        register("root") {
            id = "tallyvane.root"
            implementationClass = "tallyvane.gradle.root.TallyvaneRootPlugin"
        }
    }
}

dependencies {
    implementation(project(":graph"))
    implementation(project(":verification"))
}

tasks.named("check") {
    dependsOn("detekt")
}
